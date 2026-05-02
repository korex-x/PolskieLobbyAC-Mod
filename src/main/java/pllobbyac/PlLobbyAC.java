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
import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;

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

	// CACHE: Przechowujemy listę modów i ukrytych cheatów, żeby nie liczyć tego w kółko
	private final List<String> cachedModList = new ArrayList<>();

	// [ZABEZPIECZENIE]: Zapobiega tworzeniu nieskończonej ilości wątków Discorda
	private boolean isRpcStarted = false;

	@Override
	public void onInitializeClient() {
		// 1. BUDOWANIE CACHE: Zbieramy mody Fabrica tylko raz przy starcie gry
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			cachedModList.add(mod.getMetadata().getId());
		}

		// 2. GŁĘBOKIE SKANOWANIE: Dodajemy ewentualne ukryte klasy
		scanForHiddenCheats();

		// 3. Rejestracja kanałów
		PayloadTypeRegistry.playS2C().register(ChallengePayload.ID, ChallengePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AuthPayload.ID, AuthPayload.CODEC);

		// 4. Odpowiedź na wyzwanie od serwera
		ClientPlayNetworking.registerGlobalReceiver(ChallengePayload.ID, (payload, context) -> {
			String challenge = payload.challenge();
			String responseHash = hashString(challenge + SECRET);

			// Odsyłamy poprawnie zaszyfrowany pakiet wraz z gotowym cache modów!
			context.client().execute(() -> {
				ClientPlayNetworking.send(new AuthPayload(responseHash, cachedModList));

				// [POPRAWKA]: Odpalamy status na Discordzie tylko RAZ
				if (!isRpcStarted) {
					startDiscordRPC();
					isRpcStarted = true;
				}
			});
		});
	}

	private void startDiscordRPC() {
		new Thread(() -> {
			DiscordRPC lib = DiscordRPC.INSTANCE;
			// Pamiętaj o podmienieniu tego ID na swoje!
			String applicationId = "1500231979311038464";
			String steamId = "";

			DiscordEventHandlers handlers = new DiscordEventHandlers();
			handlers.ready = (user) -> System.out.println("Discord RPC gotowe dla: " + user.username);

			lib.Discord_Initialize(applicationId, handlers, true, steamId);

			DiscordRichPresence presence = new DiscordRichPresence();
			presence.details = "Competitive";
			// Automatyczne pobranie aktualnego czasu, aby liczyło minuty w grze
			presence.startTimestamp = System.currentTimeMillis() / 1000L;
			presence.largeImageText = "Numbani";
			presence.smallImageText = "Rogue - Level 100";
			presence.partyId = "ae488379-351d-4a4f-ad32-2b9b01c91657";
			presence.partySize = 1;
			presence.partyMax = 5;
			presence.joinSecret = "MTI4NzM0OjFpMmhuZToxMjMxMjM= ";

			lib.Discord_UpdatePresence(presence);

			while (!Thread.currentThread().isInterrupted()) {
				lib.Discord_RunCallbacks();
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					lib.Discord_Shutdown();
				}
			}
		}, "Discord-RPC-Watek").start();
	}

	private void scanForHiddenCheats() {
		String[] reversedSuspects = {
				"tneilCroeteM.tneilcroetem.tnempolevedroetem", // Meteor
				"tneilCtsruW.tneilctsruw.ten",                 // Wurst
				"kcaHhcaelB.hcaelb",                           // BleachHack
				"siotsirA.tneilc.siotsira.em",                 // Aristois
				"kcaHrehsuR.tneilc.kcahrehsur.gro",             // RusherHack
				"maceerf.maceerf",                             // Freecam
				"taen.taen",                                   // Neat (Serca)
				"htlaehorot.htlaehorot",                       // ToroHealth (Serca)
				"srotacidniegamad.srotacidniegamad",           // Damage Indicators
				"xobtih.xobtih",                               // Hitbox
				"tsissamia.tsissamia",                         // Aim Assist
				"metototua.metototua"                          // AutoTotem / AutoShield
		};

		for (String reversed : reversedSuspects) {
			String className = new StringBuilder(reversed).reverse().toString();
			try {
				Class.forName(className, false, PlLobbyAC.class.getClassLoader());
				cachedModList.add("ukryta_klasa_" + className.toLowerCase());
			} catch (ClassNotFoundException ignored) {}
		}
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

	public record ChallengePayload(String challenge) implements CustomPayload {
		public static final Id<ChallengePayload> ID = new Id<>(CHALLENGE_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, ChallengePayload> CODEC = CustomPayload.codecOf(
				(value, buf) -> {},
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
				buf -> null
		);
		@Override public Id<? extends CustomPayload> getId() { return ID; }
	}
}