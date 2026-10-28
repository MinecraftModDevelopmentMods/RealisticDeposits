package zone.moddev.mc.realisticdeposits.geometry;

/** The small part of a deposit needed to plan its abandoned workings. */
public interface MineableDepositBody {
	DepositIdentity identity();
	int centerX();
	int centerY();
	int centerZ();
	int strikeLength();
	BlockBounds bounds();
	int minimumCandidateYAt(int x, int z);
	int maximumCandidateYAt(int x, int z);
	boolean isOreAt(int x, int y, int z);
	MinePoint miningRoutePoint(double along, double lateralOffset);
}
