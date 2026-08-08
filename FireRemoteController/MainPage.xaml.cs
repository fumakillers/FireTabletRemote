using FireRemoteController.Protocol;
using FireRemoteController.Services;

namespace FireRemoteController;

public partial class MainPage : ContentPage
{
	private readonly IRemoteWebSocketClient client;

	public MainPage(IRemoteWebSocketClient client)
	{
		InitializeComponent();
		this.client = client;
		client.ConnectionChanged += OnConnectionChanged;
		client.MessageReceived += OnMessageReceived;
	}

	private async void OnConnectClicked(object? sender, EventArgs e)
	{
		if (!int.TryParse(PortEntry.Text, out var port) || port is < 1 or > 65535)
		{
			SetStatus("Port must be between 1 and 65535.");
			return;
		}

		SetBusy(true);
		try
		{
			await client.ConnectAsync(ServerIpEntry.Text?.Trim() ?? string.Empty, port);
		}
		catch (Exception error)
		{
			SetStatus($"Connection failed: {error.Message}");
		}
		finally
		{
			SetBusy(false);
		}
	}

	private async void OnDisconnectClicked(object? sender, EventArgs e)
	{
		SetBusy(true);
		try
		{
			await client.DisconnectAsync();
		}
		catch (Exception error)
		{
			SetStatus($"Disconnect failed: {error.Message}");
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
		}
	}

	private void OnPreviewTapped(object? sender, TappedEventArgs e)
	{
		var position = e.GetPosition(PreviewInputArea);
		if (position is null)
		{
			return;
		}

		SetStatus($"Preview tap: x={position.Value.X:F0}, y={position.Value.Y:F0}");
	}

	private void OnConnectionChanged(object? sender, bool connected)
	{
		MainThread.BeginInvokeOnMainThread(() =>
		{
			SetStatus(connected ? "Connected" : "Disconnected");
			ConnectButton.IsEnabled = !connected;
			DisconnectButton.IsEnabled = connected;
			SetRemoteCommandButtonsEnabled(connected);
		});
	}

	private void OnMessageReceived(object? sender, string message)
	{
		MainThread.BeginInvokeOnMainThread(() => SetStatus($"Received: {message}"));
	}

	private void SetStatus(string status)
	{
		StatusLabel.Text = $"Status: {status}";
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
