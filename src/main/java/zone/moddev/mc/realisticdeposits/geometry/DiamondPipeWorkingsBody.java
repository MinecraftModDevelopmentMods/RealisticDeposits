package zone.moddev.mc.realisticdeposits.geometry;

/** Short starter crosscuts for one pipe, without changing its ore plan. */
public final class DiamondPipeWorkingsBody implements MineableDepositBody {
	private final DepositIdentity identity;
	private final DiamondDistrict.Pipe pipe;
	private final int level;
	private final double centerX;
	private final double centerZ;
	private final double radius;
	private final double cosine;
	private final double sine;
	private final boolean rim;
	private final double bearing;
	private final double height;
	private final double phase;

	public DiamondPipeWorkingsBody(DiamondDistrict district, int pipeIndex) {
		this(district, pipeIndex, false);
	}

	public DiamondPipeWorkingsBody(DiamondDistrict district, int pipeIndex, boolean rim) {
		pipe = district.pipes().get(pipeIndex);
		DepositIdentity parent = district.identity();
		identity = new DepositIdentity(parent.worldSeed(), parent.dimension(),
				parent.definition() + "/pipe_" + pipeIndex, parent.regionX(), parent.regionZ(), parent.salt());

		level = Math.max(10, pipe.bottomY() + (int) ((pipe.topY() - pipe.bottomY()) * 0.4D));
		height = (double) (level - pipe.bottomY()) / (pipe.topY() - pipe.bottomY());
		phase = StableHash.unit(pipe.seed()) * Math.PI * 2;
		centerX = pipe.centerX() + Math.cos(phase) * pipe.upperRadius() * 0.25D * (1 - height);
		centerZ = pipe.centerZ() + Math.sin(phase) * pipe.upperRadius() * 0.25D * (1 - height);
		radius = (3 + (pipe.upperRadius() - 3) * Math.pow(height, 0.75D)) * 0.65D;

		this.rim = rim;
		bearing = StableHash.unit(identity.seed()) * Math.PI * 2;
		cosine = Math.cos(bearing);
		sine = Math.sin(bearing);
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
	public MineDecline decline(MinePoint landing, int surfaceY) {
		if (!rim) return MineableDepositBody.super.decline(landing, surfaceY);
		// Flights run tangentially. Each switchback steps outward so the
		// widening pipe doesn't swallow the stairs as they approach the surface.
		double x = landing.x() + 0.5 - centerX;
		double z = landing.z() + 0.5 - centerZ;
		int xDirection = x >= 0 ? 0 : 2;
		int zDirection = z >= 0 ? 1 : 3;
		boolean dominantX = Math.abs(x) >= Math.abs(z);
		return MineDecline.plan(landing, surfaceY, identity.seed(),
				dominantX ? zDirection : xDirection, dominantX ? xDirection : zDirection);
	}

	@Override
	public MinePoint miningRoutePoint(double along, double lateralOffset) {
		if (rim) {
			// Follow the irregular wall in country rock; the inward branch only
			// grazes the pipe. Its grade, envelope and ore coordinates stay intact.
			double angle = bearing + along / 36.0D;
			double wall = 1 + 0.10 * Math.sin(angle * 3 + phase + height * 2)
					+ 0.06 * Math.sin(angle * 7 + phase - height * 4);
			double wallRadius = (3 + (pipe.upperRadius() - 3) * Math.pow(height, 0.75D)) * wall;
			double distance = wallRadius + 3.0D + lateralOffset * 0.25D;
			return new MinePoint((int) Math.floor(centerX + distance * Math.cos(angle)), level,
					(int) Math.floor(centerZ + distance * Math.sin(angle)));
		}
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
