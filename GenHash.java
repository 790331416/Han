import at.favre.lib.crypto.bcrypt.BCrypt;
public class GenHash {
    public static void main(String[] args) {
        String hash = BCrypt.withDefaults().hashToString(10, "admin123".toCharArray());
        System.out.println("NEW_HASH=" + hash);
        // Also verify old hash
        String oldHash = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE/T/1RxrmdTBq";
        boolean oldMatch = BCrypt.verifyer().verify("admin123".toCharArray(), oldHash).verified;
        System.out.println("OLD_MATCH=" + oldMatch);
    }
}
