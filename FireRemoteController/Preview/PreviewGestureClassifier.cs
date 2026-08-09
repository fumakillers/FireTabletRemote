namespace FireRemoteController.Preview;

public enum PreviewGestureKind
{
	Tap,
	LongPress,
	Swipe,
}

public readonly record struct PreviewGesturePoint(double X, double Y);

public readonly record struct PreviewGestureDecision(
	PreviewGestureKind Kind,
	PreviewGesturePoint Start,
	PreviewGesturePoint End,
	TimeSpan Duration);

public sealed class PreviewGestureClassifier
{
	private readonly TimeSpan longPressThreshold;
	private readonly double minimumSwipeDistance;
	private bool active;
	private bool completed;
	private bool swipeCandidate;
	private PreviewGesturePoint start;
	private PreviewGesturePoint latest;

	public PreviewGestureClassifier(TimeSpan longPressThreshold, double minimumSwipeDistance)
	{
		if (longPressThreshold <= TimeSpan.Zero)
		{
			throw new ArgumentOutOfRangeException(nameof(longPressThreshold));
		}
		if (!double.IsFinite(minimumSwipeDistance) || minimumSwipeDistance <= 0)
		{
			throw new ArgumentOutOfRangeException(nameof(minimumSwipeDistance));
		}

		this.longPressThreshold = longPressThreshold;
		this.minimumSwipeDistance = minimumSwipeDistance;
	}

	public bool IsActive => active && !completed;
	public bool IsSwipeCandidate => IsActive && swipeCandidate;

	public void Press(PreviewGesturePoint point)
	{
		active = true;
		completed = false;
		swipeCandidate = false;
		start = latest = point;
	}

	public void Move(PreviewGesturePoint point)
	{
		if (!IsActive)
		{
			return;
		}

		latest = point;
		var deltaX = point.X - start.X;
		var deltaY = point.Y - start.Y;
		if (Math.Sqrt((deltaX * deltaX) + (deltaY * deltaY)) >= minimumSwipeDistance)
		{
			swipeCandidate = true;
		}
	}

	public PreviewGestureDecision? LongPressThresholdElapsed(TimeSpan duration)
	{
		if (!IsActive || swipeCandidate || duration < longPressThreshold)
		{
			return null;
		}

		completed = true;
		return new PreviewGestureDecision(PreviewGestureKind.LongPress, start, latest, duration);
	}

	public PreviewGestureDecision? Release(PreviewGesturePoint point, TimeSpan duration)
	{
		if (!IsActive)
		{
			active = false;
			return null;
		}

		Move(point);
		completed = true;
		active = false;

		if (swipeCandidate)
		{
			return new PreviewGestureDecision(PreviewGestureKind.Swipe, start, latest, duration);
		}

		return duration >= longPressThreshold
			? new PreviewGestureDecision(PreviewGestureKind.LongPress, start, latest, duration)
			: new PreviewGestureDecision(PreviewGestureKind.Tap, start, latest, duration);
	}

	public void Cancel()
	{
		active = false;
		completed = false;
		swipeCandidate = false;
	}
}
