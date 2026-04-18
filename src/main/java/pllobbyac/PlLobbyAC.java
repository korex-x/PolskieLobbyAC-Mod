package pllobbyac;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.List;

public class PlLobbyAC implements ClientModInitializer {

	// Definiujemy ID kanału
	public static final Identifier AUTH_CHANNEL = Identifier.of("polskielobby", "auth");

	@Override
	public void onInitializeClient() {
		// NAPRAWA: Zmieniamy clientSnapshot() na playC2S()
		// To rejestruje pakiet w trybie gry (Play), wysyłany od klienta do serwera.
		PayloadTypeRegistry.playC2S().register(AuthPayload.ID, AuthPayload.CODEC);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			List<String> modIds = new ArrayList<>();
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				modIds.add(mod.getMetadata().getId());
			}

			// Wysyłamy paczkę z danymi
			ClientPlayNetworking.send(new AuthPayload("MOUNT_OF_MAYHEM_SECRET_2026", modIds));
		});
	}

	// Klasa pakująca dane (Payload)
	public record AuthPayload(String key, List<String> mods) implements CustomPayload {
		public static final Id<AuthPayload> ID = new Id<>(AUTH_CHANNEL);

		public static final PacketCodec<RegistryByteBuf, AuthPayload> CODEC = CustomPayload.codecOf(
				(value, buf) -> {
					buf.writeString(value.key);
					buf.writeInt(value.mods.size());
					for (String modId : value.mods) {
						buf.writeString(modId);
					}
				},
				buf -> null // Odczyt na kliencie jest nam zbędny
		);

		@Override
		public Id<? extends CustomPayload> getId() { return ID; }
	}
}