using FireRemoteController.Preview;
using Xunit;

namespace FireRemoteController.Tests;

public sealed class PreviewCoordinateMapperTests
{
	[Fact]
	public void SameAspectRatioMapsCenterAndCorners()
	{
		AssertMapped(640, 400, 640, 400, 1920, 1200, 320, 200, 960, 600);
		AssertMapped(640, 400, 640, 400, 1920, 1200, 0, 0, 0, 0);
		AssertMapped(640, 400, 640, 400, 1920, 1200, 639.999, 399.999, 1919, 1199);
	}

	[Fact]
	public void HorizontalPaddingIsRejectedAndImageCoordinatesAreMapped()
	{
		AssertRejected(840, 240, 640, 400, 1920, 1200, 100, 120);
		AssertMapped(840, 240, 640, 400, 1920, 1200, 420, 120, 960, 600);
		AssertMapped(840, 240, 640, 400, 1920, 1200, 228, 0, 0, 0);
		AssertMapped(840, 240, 640, 400, 1920, 1200, 611.999, 239.999, 1919, 1199);
		AssertRejected(840, 240, 640, 400, 1920, 1200, 612, 120);
	}

	[Fact]
	public void VerticalPaddingIsRejectedAndImageCoordinatesAreMapped()
	{
		AssertRejected(640, 840, 640, 400, 1920, 1200, 320, 100);
		AssertMapped(640, 840, 640, 400, 1920, 1200, 320, 420, 960, 600);
		AssertMapped(640, 840, 640, 400, 1920, 1200, 0, 220, 0, 0);
		AssertMapped(640, 840, 640, 400, 1920, 1200, 639.999, 619.999, 1919, 1199);
		AssertRejected(640, 840, 640, 400, 1920, 1200, 320, 620);
	}

	[Fact]
	public void SwipeEndOutsideImageIsClampedToImageEdge()
	{
		Assert.True(PreviewCoordinateMapper.TryMapAspectFitClamped(
			840, 240, 640, 400, 1920, 1200, 100, 300, out var point));

		Assert.Equal(new FireScreenPoint(0, 1199), point);
	}

	private static void AssertMapped(
		double previewWidth,
		double previewHeight,
		int imageWidth,
		int imageHeight,
		int sourceWidth,
		int sourceHeight,
		double tapX,
		double tapY,
		int expectedX,
		int expectedY)
	{
		var mapped = PreviewCoordinateMapper.TryMapAspectFit(
			previewWidth,
			previewHeight,
			imageWidth,
			imageHeight,
			sourceWidth,
			sourceHeight,
			tapX,
			tapY,
			out var point);

		Assert.True(mapped);
		Assert.Equal(new FireScreenPoint(expectedX, expectedY), point);
	}

	private static void AssertRejected(
		double previewWidth,
		double previewHeight,
		int imageWidth,
		int imageHeight,
		int sourceWidth,
		int sourceHeight,
		double tapX,
		double tapY)
	{
		Assert.False(PreviewCoordinateMapper.TryMapAspectFit(
			previewWidth,
			previewHeight,
			imageWidth,
			imageHeight,
			sourceWidth,
			sourceHeight,
			tapX,
			tapY,
			out _));
	}
}
