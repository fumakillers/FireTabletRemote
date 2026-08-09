using FireRemoteController.Preview;
using Xunit;

namespace FireRemoteController.Tests;

public sealed class PreviewGestureClassifierTests
{
	private static readonly TimeSpan LongPressThreshold = TimeSpan.FromMilliseconds(600);

	[Fact]
	public void ShortSmallMovementProducesTap()
	{
		var classifier = CreateClassifier();
		classifier.Press(new PreviewGesturePoint(100, 100));
		classifier.Move(new PreviewGesturePoint(105, 104));

		var decision = classifier.Release(
			new PreviewGesturePoint(105, 104), TimeSpan.FromMilliseconds(200));

		Assert.Equal(PreviewGestureKind.Tap, decision?.Kind);
	}

	[Fact]
	public void LongSmallMovementProducesOneLongPressAndNoTapOnRelease()
	{
		var classifier = CreateClassifier();
		classifier.Press(new PreviewGesturePoint(100, 100));
		classifier.Move(new PreviewGesturePoint(105, 104));

		var decision = classifier.LongPressThresholdElapsed(LongPressThreshold);
		var releaseDecision = classifier.Release(
			new PreviewGesturePoint(105, 104), TimeSpan.FromMilliseconds(800));

		Assert.Equal(PreviewGestureKind.LongPress, decision?.Kind);
		Assert.Null(releaseDecision);
	}

	[Fact]
	public void SwipeDistanceProducesOneSwipeAndPreventsLongPress()
	{
		var classifier = CreateClassifier();
		classifier.Press(new PreviewGesturePoint(100, 100));
		classifier.Move(new PreviewGesturePoint(130, 100));

		var longPressDecision = classifier.LongPressThresholdElapsed(LongPressThreshold);
		var decision = classifier.Release(
			new PreviewGesturePoint(160, 100), TimeSpan.FromMilliseconds(400));
		var secondRelease = classifier.Release(
			new PreviewGesturePoint(160, 100), TimeSpan.FromMilliseconds(400));

		Assert.Null(longPressDecision);
		Assert.Equal(PreviewGestureKind.Swipe, decision?.Kind);
		Assert.Null(secondRelease);
	}

	[Fact]
	public void ReleaseAfterThresholdStillProducesLongPressWhenTimerIsLate()
	{
		var classifier = CreateClassifier();
		classifier.Press(new PreviewGesturePoint(100, 100));

		var decision = classifier.Release(
			new PreviewGesturePoint(100, 100), TimeSpan.FromMilliseconds(650));

		Assert.Equal(PreviewGestureKind.LongPress, decision?.Kind);
	}

	private static PreviewGestureClassifier CreateClassifier() =>
		new(LongPressThreshold, minimumSwipeDistance: 24);
}
