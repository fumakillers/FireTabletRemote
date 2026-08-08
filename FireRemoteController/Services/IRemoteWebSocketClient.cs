namespace FireRemoteController.Services;

public interface IRemoteWebSocketClient : IAsyncDisposable
{
	bool IsConnected { get; }
	event EventHandler<bool>? ConnectionChanged;
	event EventHandler<string>? MessageReceived;
	Task ConnectAsync(string host, int port, CancellationToken cancellationToken = default);
	Task SendAsync(string message, CancellationToken cancellationToken = default);
	Task DisconnectAsync(CancellationToken cancellationToken = default);
}
