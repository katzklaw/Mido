import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Makes Mido Pro licence keys. Needs only a JDK (Android Studio ships one).
 *
 *   java tools/KeyTool.java init                 one time: makes mido-private.key and prints the public key
 *   java tools/KeyTool.java make "Sam Smith"     prints a licence key for that name
 *   java tools/KeyTool.java check <key>          checks a key against mido-private.key's public half
 *
 * Keep mido-private.key secret and backed up. Anyone who has it can make keys, and if you lose it
 * you can't make more keys that match the public key inside the app.
 */
public class KeyTool {
    static final Path PRIVATE_FILE = Path.of("mido-private.key");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); return; }
        switch (args[0]) {
            case "init" -> init();
            case "make" -> { if (args.length < 2) usage(); else make(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))); }
            case "check" -> { if (args.length < 2) usage(); else check(args[1]); }
            default -> usage();
        }
    }

    static void usage() {
        System.out.println("usage: java tools/KeyTool.java init | make \"Name\" | check <key>");
    }

    static void init() throws Exception {
        if (Files.exists(PRIVATE_FILE)) {
            System.out.println(PRIVATE_FILE + " already exists. Not overwriting it (that would invalidate every key you've sold).");
            System.out.println("Public key for ProKeys.kt:\n" + publicKeyB64(loadPrivate()));
            return;
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(256);
        KeyPair kp = g.generateKeyPair();
        Files.writeString(PRIVATE_FILE, Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
        System.out.println("Wrote " + PRIVATE_FILE + ". Keep it secret and back it up. Never put it in the app.\n");
        System.out.println("Paste this into app/src/main/java/app/mido/pro/ProKeys.kt as PUBLIC_KEY_B64:\n");
        System.out.println(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
    }

    static void make(String name) throws Exception {
        String clean = name.replace("|", " ").trim();
        String payload = clean + "|" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        byte[] p = payload.getBytes(StandardCharsets.UTF_8);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(loadPrivate());
        s.update(p);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        System.out.println("MIDO1." + enc.encodeToString(p) + "." + enc.encodeToString(s.sign()));
    }

    static void check(String key) throws Exception {
        String[] parts = key.trim().split("\\.");
        if (parts.length != 3 || !parts[0].equals("MIDO1")) { System.out.println("INVALID (format)"); return; }
        Base64.Decoder dec = Base64.getUrlDecoder();
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64(loadPrivate())))));
        s.update(dec.decode(parts[1]));
        boolean ok = s.verify(dec.decode(parts[2]));
        System.out.println(ok ? "VALID: " + new String(dec.decode(parts[1]), StandardCharsets.UTF_8) : "INVALID (signature)");
    }

    static PrivateKey loadPrivate() throws IOException, GeneralSecurityException {
        if (!Files.exists(PRIVATE_FILE)) throw new IOException("No " + PRIVATE_FILE + " here. Run `java tools/KeyTool.java init` first.");
        byte[] der = Base64.getDecoder().decode(Files.readString(PRIVATE_FILE).trim());
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** Derives the public key from the private EC key (the private key's params + its public point). */
    static String publicKeyB64(PrivateKey priv) throws GeneralSecurityException {
        java.security.interfaces.ECPrivateKey ec = (java.security.interfaces.ECPrivateKey) priv;
        java.security.spec.ECParameterSpec params = ec.getParams();
        // Multiply the generator by the private scalar using the JDK's own point arithmetic via a KeyPair seed.
        return Base64.getEncoder().encodeToString(derive(ec, params).getEncoded());
    }

    static PublicKey derive(java.security.interfaces.ECPrivateKey ec, java.security.spec.ECParameterSpec params) throws GeneralSecurityException {
        java.math.BigInteger d = ec.getS();
        java.security.spec.ECPoint q = multiply(params.getGenerator(), d, params.getCurve());
        return KeyFactory.getInstance("EC").generatePublic(new java.security.spec.ECPublicKeySpec(q, params));
    }

    // Plain double-and-add on the curve (tiny, one-off use).
    static java.security.spec.ECPoint multiply(java.security.spec.ECPoint g, java.math.BigInteger k, java.security.spec.EllipticCurve c) {
        java.math.BigInteger p = ((java.security.spec.ECFieldFp) c.getField()).getP();
        java.math.BigInteger a = c.getA();
        java.security.spec.ECPoint r = java.security.spec.ECPoint.POINT_INFINITY;
        java.security.spec.ECPoint addend = g;
        for (int i = 0; i < k.bitLength(); i++) {
            if (k.testBit(i)) r = add(r, addend, p, a);
            addend = add(addend, addend, p, a);
        }
        return r;
    }

    static java.security.spec.ECPoint add(java.security.spec.ECPoint x, java.security.spec.ECPoint y, java.math.BigInteger p, java.math.BigInteger a) {
        if (x == java.security.spec.ECPoint.POINT_INFINITY) return y;
        if (y == java.security.spec.ECPoint.POINT_INFINITY) return x;
        java.math.BigInteger lambda;
        if (x.getAffineX().equals(y.getAffineX())) {
            if (!x.getAffineY().equals(y.getAffineY()) || x.getAffineY().signum() == 0) return java.security.spec.ECPoint.POINT_INFINITY;
            lambda = x.getAffineX().pow(2).multiply(java.math.BigInteger.valueOf(3)).add(a)
                    .multiply(x.getAffineY().shiftLeft(1).modInverse(p)).mod(p);
        } else {
            lambda = y.getAffineY().subtract(x.getAffineY())
                    .multiply(y.getAffineX().subtract(x.getAffineX()).modInverse(p)).mod(p);
        }
        java.math.BigInteger x3 = lambda.pow(2).subtract(x.getAffineX()).subtract(y.getAffineX()).mod(p);
        java.math.BigInteger y3 = lambda.multiply(x.getAffineX().subtract(x3)).subtract(x.getAffineY()).mod(p);
        return new java.security.spec.ECPoint(x3, y3);
    }
}
