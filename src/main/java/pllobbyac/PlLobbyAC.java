package pllobbyac;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class PlLobbyAC implements ClientModInitializer {

	public static final Identifier AUTH_CHANNEL = Identifier.of("polskielobby", "auth");
	public static final Identifier CHALLENGE_CHANNEL = Identifier.of("polskielobby", "challenge");
	private static final String SECRET = "MOUNT_OF_MAYHEM_SECRET_2026";

	@Override
	public void onInitializeClient() {
		// Rejestracja kanału odbierającego od serwera (S2C) i wysyłającego (C2S)
		PayloadTypeRegistry.playS2C().register(ChallengePayload.ID, ChallengePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AuthPayload.ID, AuthPayload.CODEC);

		// Nasłuchujemy wiadomości od serwera (Wyzwanie)
		ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
			String challenge = payload.challenge();
			String responseHash = hashString(challenge + SECRET);

			List<String> modIds = new ArrayList<>();
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				modIds.add(mod.getMetadata().getId());
			}

			// Odsyłamy poprawnie zaszyfrowany pakiet do serwera
			context.client().execute(() -> {
				ClientPlayNetworking.send(new AuthPayload(responseHash, modIds));
			});
		});
	}

	private static String hashString(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) hexString.append('0');
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (Exception e) {
			return "";
		}
	}

	// Rekord odbierający wyzwanie od serwera
	public record ChallengePayload(String challenge) implements CustomPayload {
		public static final Id<ChallengePayload> ID = new Id<>(CHALLENGE_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, ChallengePayload> CODEC = CustomPayload.codecOf(
				(value, buf) -> {}, // Klient nie wysyła tego pakietu
				buf -> {
					try {
						byte[] bytes = new byte[buf.readableBytes()];
						buf.readBytes(bytes);
						DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
						return new ChallengePayload(dis.readUTF());
					} catch (Exception e) {
						return new ChallengePayload("");
					}
				}
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}

	// Rekord wysyłający autoryzację do serwera (w pełni kompatybilny z Bukkitem)
	public record AuthPayload(String keyHash, List<String> mods) implements CustomPayload {
		public static final Id<AuthPayload> ID = new Id<>(AUTH_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, AuthPayload> CODEC = CustomPayload.codecOf(
				(value, buf) -> {
					try {
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						DataOutputStream dos = new DataOutputStream(baos);
						dos.writeUTF(value.keyHash);
						dos.writeInt(value.mods.size());
						for (String modId : value.mods) {
							dos.writeUTF(modId);
						}
						buf.writeBytes(baos.toByteArray());
					} catch (Exception ignored) {}
				},
				buf -> null // Klient nie odczytuje swoich własnych pakietów
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}
}