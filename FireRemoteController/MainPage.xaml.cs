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
			StatusLabel.Text = "Port must be between 1 and 65535.";
			return;
		}

		SetBusy(true);
		try
		{
			await client.ConnectAsync(ServerIpEntry.Text?.Trim() ?? string.Empty, port);
		}
		catch (Exception error)
		{
			StatusLabel.Text = $"Connection failed: {error.Message}";
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
			StatusLabel.Text = $"Disconnect failed: {error.Message}";
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
			StatusLabel.Text = $"Ping sent ({requestId[..8]}).";
		}
		catch (Exception error)
		{
			StatusLabel.Text = $"Send failed: {error.Message}";
		}
	}

	private void OnConnectionChanged(object? sender, bool connected)
	{
		MainThread.BeginInvokeOnMainThread(() =>
		{
			StatusLabel.Text = connected ? "Connected" : "Disconnected";
			ConnectButton.IsEnabled = !connected;
			DisconnectButton.IsEnabled = connected;
			SendPingButton.IsEnabled = connected;
		});
	}

	private void OnMessageReceived(object? sender, string message)
	{
		MainThread.BeginInvokeOnMainThread(() => StatusLabel.Text = $"Received: {message}");
	}

	private void SetBusy(bool busy)
	{
		ConnectButton.IsEnabled = !busy && !client.IsConnected;
		DisconnectButton.IsEnabled = !busy && client.IsConnected;
		SendPingButton.IsEnabled = !busy && client.IsConnected;
	}
}
