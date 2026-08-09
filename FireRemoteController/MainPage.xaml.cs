using FireRemoteController.Protocol;
using FireRemoteController.Preview;
using FireRemoteController.Services;
using System.Globalization;
using Microsoft.Maui.Storage;

namespace FireRemoteController;

public partial class MainPage : ContentPage
{
	private const string DefaultServerIp = "192.168.196.202";
	private const string DefaultPort = "8080";
	private const string ServerIpPreferenceKey = "connection.serverIp";
	private const string PortPreferenceKey = "connection.port";
	private static readonly TimeSpan PreviewInterval = TimeSpan.FromSeconds(1);
	private readonly IRemoteWebSocketClient client;
	private readonly object previewLoopLock = new();
	private CancellationTokenSource? previewLoopCancellation;
	private TaskCompletionSource? pendingPreviewResponse;
	private string? pendingPreviewRequestId;
	private PreviewFrame? latestPreviewFrame;

	public MainPage(IRemoteWebSocketClient client)
	{
		InitializeComponent();
		ServerIpEntry.Text = Preferences.Default.Get(ServerIpPreferenceKey, DefaultServerIp);
		PortEntry.Text = Preferences.Default.Get(PortPreferenceKey, DefaultPort);
		this.client = client;
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

	private async void OnPreviewTapped(object? sender, TappedEventArgs e)
	{
		var position = e.GetPosition(PreviewInputArea);
		if (position is null)
		{
			return;
		}

		if (!client.IsConnected)
		{
			SetStatus("Tap ignored: not connected");
			return;
		}

		var frame = latestPreviewFrame;
		if (frame is null)
		{
			SetStatus("Tap ignored: preview is not available");
			return;
		}

		if (!PreviewCoordinateMapper.TryMapAspectFit(
			PreviewInputArea.Width,
			PreviewInputArea.Height,
			frame.Width,
			frame.Height,
			frame.SourceWidth,
			frame.SourceHeight,
			position.Value.X,
			position.Value.Y,
			out var firePoint))
		{
			SetStatus("Tap ignored: outside preview image");
			return;
		}

		try
		{
			var requestId = $"tap-{Guid.NewGuid():N}";
			await client.SendAsync(RemoteCommandJson.CreateTap(firePoint.X, firePoint.Y, requestId));
			SetStatus($"Tap sent: x={firePoint.X}, y={firePoint.Y}");
		}
		catch (Exception error)
		{
			SetStatus($"Tap failed: {error.Message}");
			SetConnectionError();
		}
	}

	private void OnConnectionChanged(object? sender, bool connected)
	{
		if (connected)
		{
			StartPreviewLoop();
		}
		else
		{
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
				latestPreviewFrame = frame;
				PreviewImage.Source = ImageSource.FromStream(
					() => new MemoryStream(frame.Data, writable: false));
				PreviewPlaceholder.IsVisible = false;
				SetStatus($"Preview: {frame.Width}x{frame.Height}, {frame.Data.Length} bytes");
				break;
			case PreviewError error:
				SetStatus($"Preview unavailable: {error.Message}");
				break;
		}
	}

	private void SetStatus(string status)
	{
		StatusLabel.Text = $"Status: {status}";
		System.Diagnostics.Debug.WriteLine(StatusLabel.Text);
	}

	private void SetConnectionError()
	{
		ConnectionStateLabel.Text = "Error";
		ConnectionStateLabel.TextColor = Color.FromArgb("#EF5350");
	}

	private void SetConnectionState(string status, bool connected)
	{
		ConnectionStateLabel.Text = status;
		ConnectionStateLabel.TextColor = Color.FromArgb(connected ? "#4CAF50" : "#9E9E9E");
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
