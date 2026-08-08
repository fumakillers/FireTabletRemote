using FireRemoteController.Services;
using Microsoft.Extensions.Logging;
#if ANDROID
using Android.Text;
using Android.Text.Method;
using Microsoft.Maui.Handlers;
#endif

namespace FireRemoteController;

public static class MauiProgram
{
	public static MauiApp CreateMauiApp()
	{
		var builder = MauiApp.CreateBuilder();
		builder
			.UseMauiApp<App>()
			.ConfigureFonts(fonts =>
			{
				fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
				fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
			});

#if ANDROID
		EntryHandler.Mapper.AppendToMapping("AsciiConnectionInput", (handler, entry) =>
		{
			if (entry.AutomationId == "ServerIpEntry")
			{
				handler.PlatformView.SetRawInputType(InputTypes.ClassNumber | InputTypes.NumberFlagDecimal);
				handler.PlatformView.KeyListener = DigitsKeyListener.GetInstance("0123456789.");
			}
			else if (entry.AutomationId == "PortEntry")
			{
				handler.PlatformView.SetRawInputType(InputTypes.ClassNumber);
				handler.PlatformView.KeyListener = DigitsKeyListener.GetInstance("0123456789");
			}
		});
#endif

		builder.Services.AddSingleton<IRemoteWebSocketClient, RemoteWebSocketClient>();
		builder.Services.AddSingleton<MainPage>();

#if DEBUG
		builder.Logging.AddDebug();
#endif
		return builder.Build();
	}
}
