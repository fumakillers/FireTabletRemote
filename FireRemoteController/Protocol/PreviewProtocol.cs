using System.Text.Json;

namespace FireRemoteController.Protocol;

public abstract record PreviewResponse(string? RequestId);

public sealed record PreviewFrame(
	string? RequestId,
	string MimeType,
	int Width,
	int Height,
	int SourceWidth,
	int SourceHeight,
	byte[] Data) : PreviewResponse(RequestId);

public sealed record PreviewError(string? RequestId, string Message) : PreviewResponse(RequestId);

public static class PreviewProtocol
{
	public static string CreateRequest(string requestId) => JsonSerializer.Serialize(new
	{
		version = 1,
		type = "previewRequest",
		requestId
	});

	public static bool TryParseResponse(string message, out PreviewResponse? response)
	{
		response = null;
		try
		{
			using var document = JsonDocument.Parse(message);
			var root = document.RootElement;
			if (!root.TryGetProperty("version", out var version) || version.GetInt32() != 1
				|| !root.TryGetProperty("type", out var typeElement))
			{
				return false;
			}

			var type = typeElement.GetString();
			var requestId = root.TryGetProperty("requestId", out var requestIdElement)
				? requestIdElement.GetString()
				: null;
			if (type == "previewFrame")
			{
				var mimeType = root.GetProperty("mimeType").GetString();
				var width = root.GetProperty("width").GetInt32();
				var height = root.GetProperty("height").GetInt32();
				var sourceWidth = root.GetProperty("sourceWidth").GetInt32();
				var sourceHeight = root.GetProperty("sourceHeight").GetInt32();
				var data = Convert.FromBase64String(root.GetProperty("data").GetString() ?? string.Empty);
				if (mimeType != "image/jpeg" || width <= 0 || height <= 0
					|| sourceWidth <= 0 || sourceHeight <= 0 || data.Length == 0)
				{
					return false;
				}

				response = new PreviewFrame(
					requestId,
					mimeType,
					width,
					height,
					sourceWidth,
					sourceHeight,
					data);
				return true;
			}

			if (type == "previewError")
			{
				response = new PreviewError(
					requestId,
					root.GetProperty("message").GetString() ?? "Preview request failed");
				return true;
			}

			return false;
		}
		catch (JsonException)
		{
			return false;
		}
		catch (FormatException)
		{
			return false;
		}
		catch (InvalidOperationException)
		{
			return false;
		}
		catch (KeyNotFoundException)
		{
			return false;
		}
		catch (OverflowException)
		{
			return false;
		}
	}
}
