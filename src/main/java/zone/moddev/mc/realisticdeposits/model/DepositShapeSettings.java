package zone.moddev.mc.realisticdeposits.model;

import com.google.gson.JsonObject;

/** Settings shared by deposit catalogues, without tying every shape to iron. */
public interface DepositShapeSettings {
	String type();
	JsonObject toJson();
}
