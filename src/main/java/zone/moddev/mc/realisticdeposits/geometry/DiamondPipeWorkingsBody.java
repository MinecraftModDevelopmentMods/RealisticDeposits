package zone.moddev.mc.realisticdeposits.geometry;

/** A shaft and short crosscuts through one pipe, without changing its ore plan. */
public final class DiamondPipeWorkingsBody implements MineableDepositBody {
	private final DepositIdentity identity;
	private final DiamondDistrict.Pipe pipe;
	private final int level;
	private final double centerX;
	private final double centerZ;
	private final double radius;
	private final double cosine;
	private final double sine;

	public DiamondPipeWorkingsBody(DiamondDistrict district, int pipeIndex) {
		pipe = district.pipes().get(pipeIndex);
		DepositIdentity parent = district.identity();
		identity = new DepositIdentity(parent.worldSeed(), parent.dimension(),
				parent.definition() + "/pipe_" + pipeIndex, parent.regionX(), parent.regionZ(), parent.salt());

		level = Math.max(10, pipe.bottomY() + (int) ((pipe.topY() - pipe.bottomY()) * 0.4D));
		double height = (double) (level - pipe.bottomY()) / (pipe.topY() - pipe.bottomY());
		double phase = StableHash.unit(pipe.seed()) * Math.PI * 2;
		centerX = pipe.centerX() + Math.cos(phase) * pipe.upperRadius() * 0.25D * (1 - height);
		centerZ = pipe.centerZ() + Math.sin(phase) * pipe.upperRadius() * 0.25D * (1 - height);
		radius = (3 + (pipe.upperRadius() - 3) * Math.pow(height, 0.75D)) * 0.65D;

		double direction = StableHash.unit(identity.seed()) * Math.PI * 2;
		cosine = Math.cos(direction);
		sine = Math.sin(direction);
	}

	@Override public DepositIdentity identity() { return identity; }
	@Override public int centerX() { return (int) Math.round(centerX); }
	@Override public int centerY() { return level; }
	@Override public int centerZ() { return (int) Math.round(centerZ); }
	@Override public int strikeLength() { return (int) Math.ceil(radius * 2); }
	@Override public BlockBounds bounds() { return pipe.bounds(); }
	@Override public int minimumCandidateYAt(int x, int z) { return pipe.bottomY(); }
	@Override public int maximumCandidateYAt(int x, int z) { return pipe.topY(); }
	@Override public boolean isOreAt(int x, int y, int z) { return pipe.isOreAt(x, y, z); }

	@Override
	public MinePoint miningRoutePoint(double along, double lateralOffset) {
		// Keep the galleries inside the wider part of the pipe. The surface
		// ladder reaches this level; miners can then follow the ore downwards.
		double forward = along * radius / 36.0D;
		double side = lateralOffset * radius / 24.0D;
		double length = Math.hypot(forward, side);

		if (length > radius) {
			forward *= radius / length;
			side *= radius / length;
		}

		return new MinePoint((int) Math.round(centerX + forward * cosine - side * sine), level,
				(int) Math.round(centerZ + forward * sine + side * cosine));
	}
}
