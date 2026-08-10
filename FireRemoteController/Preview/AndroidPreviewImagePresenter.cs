using Android.Graphics;
using Android.Widget;

namespace FireRemoteController.Preview;

internal sealed class AndroidPreviewImagePresenter
{
	private readonly Image image;
	private Bitmap? displayedBitmap;
	private long updateGeneration;

	public AndroidPreviewImagePresenter(Image image)
	{
		this.image = image;
	}

	public async Task<bool> TryDisplayAsync(byte[] data, Action onDisplayed)
	{
		ArgumentNullException.ThrowIfNull(data);
		ArgumentNullException.ThrowIfNull(onDisplayed);

		var generation = Interlocked.Increment(ref updateGeneration);
		Bitmap? preparedBitmap = null;
		try
		{
			preparedBitmap = await Task.Run(() =>
				BitmapFactory.DecodeByteArray(data, 0, data.Length));
			if (preparedBitmap is null)
			{
				throw new InvalidDataException("The preview image could not be decoded.");
			}

			return await MainThread.InvokeOnMainThreadAsync(() =>
			{
				if (generation != Volatile.Read(ref updateGeneration)
					|| image.Handler?.PlatformView is not ImageView platformImage)
				{
					return false;
				}

				var previousBitmap = displayedBitmap;
				platformImage.SetImageBitmap(preparedBitmap);
				displayedBitmap = preparedBitmap;
				preparedBitmap = null;
				previousBitmap?.Dispose();
				onDisplayed();
				return true;
			});
		}
		catch when (generation != Volatile.Read(ref updateGeneration))
		{
			return false;
		}
		finally
		{
			preparedBitmap?.Dispose();
		}
	}

	public void Clear()
	{
		Interlocked.Increment(ref updateGeneration);
		if (image.Handler?.PlatformView is ImageView platformImage)
		{
			platformImage.SetImageDrawable(null);
		}

		displayedBitmap?.Dispose();
		displayedBitmap = null;
	}
}
