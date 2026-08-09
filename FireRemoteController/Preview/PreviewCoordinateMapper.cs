namespace FireRemoteController.Preview;

public readonly record struct FireScreenPoint(int X, int Y);

public static class PreviewCoordinateMapper
{
	public static bool TryMapAspectFit(
		double previewWidth,
		double previewHeight,
		int imageWidth,
		int imageHeight,
		int sourceWidth,
		int sourceHeight,
		double tapX,
		double tapY,
		out FireScreenPoint point)
	{
		point = default;
		if (!IsPositiveFinite(previewWidth) || !IsPositiveFinite(previewHeight)
			|| imageWidth <= 0 || imageHeight <= 0
			|| sourceWidth <= 0 || sourceHeight <= 0
			|| !double.IsFinite(tapX) || !double.IsFinite(tapY))
		{
			return false;
		}

		var rectangle = GetDisplayedImageRectangle(previewWidth, previewHeight, imageWidth, imageHeight);

		if (tapX < rectangle.X || tapX >= rectangle.Right
			|| tapY < rectangle.Y || tapY >= rectangle.Bottom)
		{
			return false;
		}

		point = MapWithinRectangle(rectangle, sourceWidth, sourceHeight, tapX, tapY);
		return true;
	}

	public static bool TryMapAspectFitClamped(
		double previewWidth,
		double previewHeight,
		int imageWidth,
		int imageHeight,
		int sourceWidth,
		int sourceHeight,
		double pointX,
		double pointY,
		out FireScreenPoint point)
	{
		point = default;
		if (!IsPositiveFinite(previewWidth) || !IsPositiveFinite(previewHeight)
			|| imageWidth <= 0 || imageHeight <= 0
			|| sourceWidth <= 0 || sourceHeight <= 0
			|| !double.IsFinite(pointX) || !double.IsFinite(pointY))
		{
			return false;
		}

		var rectangle = GetDisplayedImageRectangle(previewWidth, previewHeight, imageWidth, imageHeight);
		var clampedX = Math.Clamp(pointX, rectangle.X, Math.BitDecrement(rectangle.Right));
		var clampedY = Math.Clamp(pointY, rectangle.Y, Math.BitDecrement(rectangle.Bottom));
		point = MapWithinRectangle(rectangle, sourceWidth, sourceHeight, clampedX, clampedY);
		return true;
	}

	private static FireScreenPoint MapWithinRectangle(
		DisplayedImageRectangle rectangle,
		int sourceWidth,
		int sourceHeight,
		double pointX,
		double pointY)
	{
		var normalizedX = (pointX - rectangle.X) / rectangle.Width;
		var normalizedY = (pointY - rectangle.Y) / rectangle.Height;
		var fireX = Math.Clamp((int)Math.Floor(normalizedX * sourceWidth), 0, sourceWidth - 1);
		var fireY = Math.Clamp((int)Math.Floor(normalizedY * sourceHeight), 0, sourceHeight - 1);
		return new FireScreenPoint(fireX, fireY);
	}

	private static DisplayedImageRectangle GetDisplayedImageRectangle(
		double previewWidth,
		double previewHeight,
		int imageWidth,
		int imageHeight)
	{
		var scale = Math.Min(previewWidth / imageWidth, previewHeight / imageHeight);
		var width = imageWidth * scale;
		var height = imageHeight * scale;
		return new DisplayedImageRectangle(
			(previewWidth - width) / 2d,
			(previewHeight - height) / 2d,
			width,
			height);
	}

	private static bool IsPositiveFinite(double value) => value > 0 && double.IsFinite(value);

	private readonly record struct DisplayedImageRectangle(double X, double Y, double Width, double Height)
	{
		public double Right => X + Width;
		public double Bottom => Y + Height;
	}
}
