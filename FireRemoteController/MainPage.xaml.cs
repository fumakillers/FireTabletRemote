using FireRemoteController.Protocol;
using FireRemoteController.Preview;
using FireRemoteController.Services;
using System.Diagnostics;
using System.Globalization;
using Microsoft.Maui.Storage;

namespace FireRemoteController;

public partial class MainPage : ContentPage
{
	private const string DefaultServerIp = "192.168.196.202";
	private const string DefaultPort = "8080";
	private const string ServerIpPreferenceKey = "connection.serverIp";
	private const string PortPreferenceKey = "connection.port";
	private const double MinimumSwipeDistance = 24d;
	private const int LongPressDurationMs = 600;
	private const int MinimumSwipeDurationMs = 100;
	private const int MaximumSwipeDurationMs = 2_000;
	private static readonly TimeSpan PreviewInterval = TimeSpan.FromSeconds(1);
	private static readonly TimeSpan LongPressThreshold = TimeSpan.FromMilliseconds(LongPressDurationMs);
	private readonly IRemoteWebSocketClient client;
	private readonly AndroidPreviewImagePresenter previewImagePresenter;
	private readonly PreviewGestureClassifier previewGestureClassifier =
		new(LongPressThreshold, MinimumSwipeDistance);
	private readonly object previewLoopLock = new();
	private CancellationTokenSource? previewLoopCancellation;
	private TaskCompletionSource? pendingPreviewResponse;
	private string? pendingPreviewRequestId;
	private PreviewFrame? latestPreviewFrame;
	private PreviewFrame? gesturePreviewFrame;
	private CancellationTokenSource? longPressCancellation;
	private long gestureStartTimestamp;
	private double gesturePreviewWidth;
	private double gesturePreviewHeight;

	public MainPage(IRemoteWebSocketClient client)
	{
		InitializeComponent();
		ServerIpEntry.Text = Preferences.Default.Get(ServerIpPreferenceKey, DefaultServerIp);
		PortEntry.Text = Preferences.Default.Get(PortPreferenceKey, DefaultPort);
		this.client = client;
		previewImagePresenter = new AndroidPreviewImagePresenter(PreviewImage);
		client.ConnectionChanged += OnConnectionChanged;
		client.MessageReceived += OnMessageReceived;
		SetConnectionState(client.IsConnected ? "Connected" : "Disconnected", client.IsConnected);
	}

	protected override void OnAppearing()
	{
		base.OnAppearing();
		StartPreviewLoop();
	}

	protected override void OnDisappearing()
	{
		CancelPreviewGesture();
		StopPreviewLoop();
		base.OnDisappearing();
	}

	private void OnSettingsClicked(object? sender, EventArgs e)
	{
		SettingsOverlay.IsVisible = true;
	}

	private void OnCloseSettingsClicked(object? sender, EventArgs e)
	{
		SaveConnectionSettingsIfValid();
		ServerIpEntry.Unfocus();
		PortEntry.Unfocus();
		SettingsOverlay.IsVisible = false;
	}

	private async void OnConnectClicked(object? sender, EventArgs e)
	{
		var serverIp = ServerIpEntry.Text?.Trim() ?? string.Empty;
		if (!IsValidIpv4Address(serverIp))
		{
			SetStatus("Server IP must be a valid IPv4 address (for example, 192.168.196.202).");
			SetConnectionError();
			return;
		}

		if (!TryParsePort(PortEntry.Text, out var port))
		{
			SetStatus("Port must be between 1 and 65535.");
			SetConnectionError();
			return;
		}

		SaveConnectionSettings(serverIp, port);

		SetBusy(true);
		try
		{
			await client.ConnectAsync(serverIp, port);
		}
		catch (TimeoutException)
		{
			SetStatus("Connection timed out");
			SetConnectionError();
		}
		catch (Exception error)
		{
			SetStatus($"Connection failed: {error.Message}");
			SetConnectionError();
		}
		finally
		{
			SetBusy(false);
		}
	}

	private void OnServerIpTextChanged(object? sender, TextChangedEventArgs e)
	{
		SanitizeEntry((Entry)sender!, e.NewTextValue, character =>
			character is >= '0' and <= '9' || character == '.');
	}

	private void OnPortTextChanged(object? sender, TextChangedEventArgs e)
	{
		SanitizeEntry((Entry)sender!, e.NewTextValue, character =>
			character is >= '0' and <= '9');
	}

	private static void SanitizeEntry(Entry entry, string? text, Func<char, bool> isAllowed)
	{
		var sanitized = string.Concat((text ?? string.Empty).Where(isAllowed));
		if (!string.Equals(entry.Text, sanitized, StringComparison.Ordinal))
		{
			entry.Text = sanitized;
		}
	}

	private static bool IsValidIpv4Address(string serverIp)
	{
		var octets = serverIp.Split('.');
		return octets.Length == 4 && octets.All(octet =>
			octet.Length is > 0 and <= 3
			&& octet.All(character => character is >= '0' and <= '9')
			&& int.TryParse(octet, NumberStyles.None, CultureInfo.InvariantCulture, out var value)
			&& value <= 255);
	}

	private static bool TryParsePort(string? text, out int port)
	{
		return int.TryParse(text, NumberStyles.None, CultureInfo.InvariantCulture, out port)
			&& port is >= 1 and <= 65535;
	}

	private void SaveConnectionSettingsIfValid()
	{
		var serverIp = ServerIpEntry.Text?.Trim() ?? string.Empty;
		if (IsValidIpv4Address(serverIp) && TryParsePort(PortEntry.Text, out var port))
		{
			SaveConnectionSettings(serverIp, port);
		}
	}

	private static void SaveConnectionSettings(string serverIp, int port)
	{
		Preferences.Default.Set(ServerIpPreferenceKey, serverIp);
		Preferences.Default.Set(PortPreferenceKey, port.ToString(CultureInfo.InvariantCulture));
	}

	private async void OnDisconnectClicked(object? sender, EventArgs e)
	{
		StopPreviewLoop();
		SetBusy(true);
		try
		{
			await client.DisconnectAsync();
		}
		catch (Exception error)
		{
			SetStatus($"Disconnect failed: {error.Message}");
			SetConnectionError();
		}
		finally
		{
			SetBusy(false);
		}
	}

	private async void OnSendPingClicked(object? sender, EventArgs e)
	{
		try
		{
			var requestId = Guid.NewGuid().ToString("N");
			await client.SendAsync(RemoteCommandJson.CreatePing(requestId));
			SetStatus($"Ping sent ({requestId[..8]}).");
		}
		catch (Exception error)
		{
			SetStatus($"Send failed: {error.Message}");
			SetConnectionError();
		}
	}

	private async void OnBackClicked(object? sender, EventArgs e) =>
		await SendNavigationCommandAsync("Back", RemoteCommandJson.CreateBack);

	private async void OnHomeClicked(object? sender, EventArgs e) =>
		await SendNavigationCommandAsync("Home", RemoteCommandJson.CreateHome);

	private async void OnRecentsClicked(object? sender, EventArgs e) =>
		await SendNavigationCommandAsync("Recents", RemoteCommandJson.CreateRecents);

	private async Task SendNavigationCommandAsync(
		string displayName,
		Func<string?, string> createCommand)
	{
		try
		{
			var requestId = Guid.NewGuid().ToString("N");
			await client.SendAsync(createCommand(requestId));
			SetStatus($"{displayName} sent ({requestId[..8]}).");
		}
		catch (Exception error)
		{
			SetStatus($"{displayName} failed: {error.Message}");
			SetConnectionError();
		}
	}

	private void OnPreviewPointerPressed(object? sender, PointerEventArgs e)
	{
		var position = e.GetPosition(PreviewInputArea);
		if (position is null || HasMultiplePointers(e))
		{
			CancelPreviewGesture();
			return;
		}

		if (!client.IsConnected)
		{
			SetStatus("Gesture ignored: not connected");
			return;
		}

		var frame = latestPreviewFrame;
		if (frame is null)
		{
			SetStatus("Gesture ignored: preview is not available");
			return;
		}

		gesturePreviewWidth = PreviewInputArea.Width;
		gesturePreviewHeight = PreviewInputArea.Height;
		if (!TryMapPreviewPoint(frame, position.Value.X, position.Value.Y, false, out _))
		{
			SetStatus("Gesture ignored: outside preview image");
			gesturePreviewWidth = 0;
			gesturePreviewHeight = 0;
			return;
		}

		CancelPreviewGesture();
		gesturePreviewFrame = frame;
		gesturePreviewWidth = PreviewInputArea.Width;
		gesturePreviewHeight = PreviewInputArea.Height;
		gestureStartTimestamp = Stopwatch.GetTimestamp();
		previewGestureClassifier.Press(new PreviewGesturePoint(position.Value.X, position.Value.Y));
		longPressCancellation = new CancellationTokenSource();
		_ = DetectLongPressAsync(longPressCancellation);
	}

	private void OnPreviewPointerMoved(object? sender, PointerEventArgs e)
	{
		if (!previewGestureClassifier.IsActive)
		{
			return;
		}

		if (HasMultiplePointers(e))
		{
			CancelPreviewGesture();
			return;
		}

		var position = e.GetPosition(PreviewInputArea);
		if (position is null)
		{
			return;
		}

		previewGestureClassifier.Move(new PreviewGesturePoint(position.Value.X, position.Value.Y));
		if (previewGestureClassifier.IsSwipeCandidate)
		{
			longPressCancellation?.Cancel();
		}
	}

	private void OnPreviewPointerReleased(object? sender, PointerEventArgs e)
	{
		if (!previewGestureClassifier.IsActive || HasMultiplePointers(e))
		{
			CancelPreviewGesture();
			return;
		}

		var position = e.GetPosition(PreviewInputArea);
		if (position is null)
		{
			CancelPreviewGesture();
			return;
		}

		longPressCancellation?.Cancel();
		longPressCancellation?.Dispose();
		longPressCancellation = null;
		var decision = previewGestureClassifier.Release(
			new PreviewGesturePoint(position.Value.X, position.Value.Y),
			Stopwatch.GetElapsedTime(gestureStartTimestamp));
		if (decision is not null)
		{
			_ = SendPreviewGestureAsync(decision.Value);
		}
	}

	private void OnPreviewPointerExited(object? sender, PointerEventArgs e) => CancelPreviewGesture();

	private async Task DetectLongPressAsync(CancellationTokenSource cancellation)
	{
		try
		{
			await Task.Delay(LongPressThreshold, cancellation.Token);
			if (!ReferenceEquals(longPressCancellation, cancellation))
			{
				return;
			}

			var decision = previewGestureClassifier.LongPressThresholdElapsed(
				Stopwatch.GetElapsedTime(gestureStartTimestamp));
			if (decision is not null)
			{
				await SendPreviewGestureAsync(decision.Value);
			}
		}
		catch (OperationCanceledException) when (cancellation.IsCancellationRequested)
		{
		}
		finally
		{
			if (ReferenceEquals(longPressCancellation, cancellation))
			{
				longPressCancellation = null;
			}
			cancellation.Dispose();
		}
	}

	private async Task SendPreviewGestureAsync(PreviewGestureDecision decision)
	{
		var frame = gesturePreviewFrame;
		if (!client.IsConnected || frame is null)
		{
			SetStatus("Gesture ignored: connection or preview is unavailable");
			return;
		}

		if (!TryMapPreviewPoint(frame, decision.Start.X, decision.Start.Y, false, out var start))
		{
			SetStatus("Gesture ignored: start is outside preview image");
			return;
		}

		try
		{
			switch (decision.Kind)
			{
				case PreviewGestureKind.Tap:
					await client.SendAsync(RemoteCommandJson.CreateTap(
						start.X, start.Y, $"tap-{Guid.NewGuid():N}"));
					SetStatus($"Tap sent: x={start.X}, y={start.Y}");
					break;
				case PreviewGestureKind.LongPress:
					await client.SendAsync(RemoteCommandJson.CreateLongPress(
						start.X, start.Y, LongPressDurationMs, $"hold-{Guid.NewGuid():N}"));
					SetStatus($"Long press sent: x={start.X}, y={start.Y}");
					break;
				case PreviewGestureKind.Swipe:
					if (!TryMapPreviewPoint(frame, decision.End.X, decision.End.Y, true, out var end))
					{
						SetStatus("Swipe ignored: preview coordinates are unavailable");
						return;
					}
					var durationMs = Math.Clamp(
						(int)Math.Round(decision.Duration.TotalMilliseconds),
						MinimumSwipeDurationMs,
						MaximumSwipeDurationMs);
					await client.SendAsync(RemoteCommandJson.CreateSwipe(
						start.X, start.Y, end.X, end.Y, durationMs, $"swipe-{Guid.NewGuid():N}"));
					SetStatus($"Swipe sent: ({start.X},{start.Y}) to ({end.X},{end.Y})");
					break;
			}
		}
		catch (Exception error)
		{
			SetStatus($"Gesture failed: {error.Message}");
			SetConnectionError();
		}
	}

	private bool TryMapPreviewPoint(
		PreviewFrame frame,
		double x,
		double y,
		bool clamp,
		out FireScreenPoint point)
	{
		return clamp
			? PreviewCoordinateMapper.TryMapAspectFitClamped(
				gesturePreviewWidth, gesturePreviewHeight,
				frame.Width, frame.Height, frame.SourceWidth, frame.SourceHeight,
				x, y, out point)
			: PreviewCoordinateMapper.TryMapAspectFit(
				gesturePreviewWidth, gesturePreviewHeight,
				frame.Width, frame.Height, frame.SourceWidth, frame.SourceHeight,
				x, y, out point);
	}

	private void CancelPreviewGesture()
	{
		previewGestureClassifier.Cancel();
		longPressCancellation?.Cancel();
		longPressCancellation?.Dispose();
		longPressCancellation = null;
		gesturePreviewFrame = null;
		gesturePreviewWidth = 0;
		gesturePreviewHeight = 0;
	}

	private static bool HasMultiplePointers(PointerEventArgs e)
	{
#if ANDROID
		return e.PlatformArgs?.MotionEvent?.PointerCount > 1;
#else
		return false;
#endif
	}

	private void OnConnectionChanged(object? sender, bool connected)
	{
		if (connected)
		{
			StartPreviewLoop();
		}
		else
		{
			CancelPreviewGesture();
			StopPreviewLoop();
		}

		MainThread.BeginInvokeOnMainThread(() =>
		{
			SetStatus(connected ? "Connected" : "Disconnected");
			SetConnectionState(connected ? "Connected" : "Disconnected", connected);
			ConnectButton.IsEnabled = !connected;
			DisconnectButton.IsEnabled = connected;
			SetRemoteCommandButtonsEnabled(connected);
			if (!connected)
			{
				latestPreviewFrame = null;
				PreviewImage.Source = null;
				previewImagePresenter.Clear();
				PreviewPlaceholder.IsVisible = true;
			}
		});
	}

	private void OnMessageReceived(object? sender, string message)
	{
		if (PreviewProtocol.TryParseResponse(message, out var previewResponse))
		{
			if (!CompletePreviewRequest(previewResponse!.RequestId))
			{
				return;
			}
			MainThread.BeginInvokeOnMainThread(() => DisplayPreviewResponse(previewResponse));
			return;
		}

		MainThread.BeginInvokeOnMainThread(() => SetStatus($"Received: {message}"));
	}

	private void StartPreviewLoop()
	{
		lock (previewLoopLock)
		{
			if (!client.IsConnected || previewLoopCancellation is not null)
			{
				return;
			}

			previewLoopCancellation = new CancellationTokenSource();
			_ = RunPreviewLoopAsync(previewLoopCancellation);
		}
	}

	private void StopPreviewLoop()
	{
		CancellationTokenSource? cancellation;
		lock (previewLoopLock)
		{
			cancellation = previewLoopCancellation;
			previewLoopCancellation = null;
			pendingPreviewResponse?.TrySetCanceled();
			pendingPreviewResponse = null;
			pendingPreviewRequestId = null;
		}

		cancellation?.Cancel();
	}

	private async Task RunPreviewLoopAsync(CancellationTokenSource cancellation)
	{
		try
		{
			while (!cancellation.IsCancellationRequested && client.IsConnected)
			{
				var requestId = $"preview-{Guid.NewGuid():N}";
				var completion = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
				lock (previewLoopLock)
				{
					if (!ReferenceEquals(previewLoopCancellation, cancellation))
					{
						return;
					}

					pendingPreviewRequestId = requestId;
					pendingPreviewResponse = completion;
				}

				await client.SendAsync(PreviewProtocol.CreateRequest(requestId), cancellation.Token);
				await completion.Task.WaitAsync(cancellation.Token);
				await Task.Delay(PreviewInterval, cancellation.Token);
			}
		}
		catch (OperationCanceledException) when (cancellation.IsCancellationRequested)
		{
		}
		catch (Exception error)
		{
			MainThread.BeginInvokeOnMainThread(() => SetStatus($"Preview stopped: {error.Message}"));
		}
		finally
		{
			lock (previewLoopLock)
			{
				if (ReferenceEquals(previewLoopCancellation, cancellation))
				{
					previewLoopCancellation = null;
					pendingPreviewResponse = null;
					pendingPreviewRequestId = null;
				}
			}
			cancellation.Dispose();
		}
	}

	private bool CompletePreviewRequest(string? requestId)
	{
		lock (previewLoopLock)
		{
			if (requestId == pendingPreviewRequestId)
			{
				var completion = pendingPreviewResponse;
				pendingPreviewResponse = null;
				pendingPreviewRequestId = null;
				return completion?.TrySetResult() == true;
			}

			return false;
		}
	}

	private void DisplayPreviewResponse(PreviewResponse response)
	{
		switch (response)
		{
			case PreviewFrame frame:
				_ = DisplayPreviewFrameAsync(frame);
				break;
			case PreviewError error:
				SetStatus($"Preview unavailable: {error.Message}");
				break;
		}
	}

	private async Task DisplayPreviewFrameAsync(PreviewFrame frame)
	{
		try
		{
			await previewImagePresenter.TryDisplayAsync(frame.Data, () =>
			{
				latestPreviewFrame = frame;
				PreviewPlaceholder.IsVisible = false;
				SetStatus($"Preview: {frame.Width}x{frame.Height}, {frame.Data.Length} bytes");
			});
		}
		catch (Exception error)
		{
			MainThread.BeginInvokeOnMainThread(() =>
				SetStatus($"Preview image failed: {error.Message}"));
		}
	}

	private void SetStatus(string status)
	{
		StatusLabel.Text = $"Status: {status}";
		System.Diagnostics.Debug.WriteLine(StatusLabel.Text);
	}

	private void SetConnectionError()
	{
		SetConnectionIndicator("Connection error", "wifi_error.svg");
	}

	private void SetConnectionState(string status, bool connected)
	{
		SetConnectionIndicator(
			status,
			connected ? "wifi_connected.svg" : "wifi_disconnected.svg");
	}

	private void SetConnectionIndicator(string description, string imageSource)
	{
		ConnectionStateIndicator.Source = imageSource;
		SemanticProperties.SetDescription(ConnectionStateIndicator, description);
	}

	private void SetBusy(bool busy)
	{
		ConnectButton.IsEnabled = !busy && !client.IsConnected;
		DisconnectButton.IsEnabled = !busy && client.IsConnected;
		SetRemoteCommandButtonsEnabled(!busy && client.IsConnected);
	}

	private void SetRemoteCommandButtonsEnabled(bool enabled)
	{
		SendPingButton.IsEnabled = enabled;
		BackButton.IsEnabled = enabled;
		HomeButton.IsEnabled = enabled;
		RecentsButton.IsEnabled = enabled;
	}
}
