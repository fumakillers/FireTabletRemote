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

		var scale = Math.Min(previewWidth / imageWidth, previewHeight / imageHeight);
		var displayedWidth = imageWidth * scale;
		var displayedHeight = imageHeight * scale;
		var displayedX = (previewWidth - displayedWidth) / 2d;
		var displayedY = (previewHeight - displayedHeight) / 2d;

		if (tapX < displayedX || tapX >= displayedX + displayedWidth
			|| tapY < displayedY || tapY >= displayedY + displayedHeight)
		{
			return false;
		}

		var normalizedX = (tapX - displayedX) / displayedWidth;
		var normalizedY = (tapY - displayedY) / displayedHeight;
		var fireX = Math.Clamp((int)Math.Floor(normalizedX * sourceWidth), 0, sourceWidth - 1);
		var fireY = Math.Clamp((int)Math.Floor(normalizedY * sourceHeight), 0, sourceHeight - 1);
		point = new FireScreenPoint(fireX, fireY);
		return true;
	}

	private static bool IsPositiveFinite(double value) => value > 0 && double.IsFinite(value);
}
