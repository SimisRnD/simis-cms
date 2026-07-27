import com.simisinc.platform.application.UserPasswordCommand;

public class VerifyPasswordTest {
  public static void main(String[] args) {
    String storedHash = "$argon2id$v=19$m=65536,t=2,p=1$DpuN8gsASaDezGZJrHdGmw$/FEPWK9av1Iwqc42YFOs2lF7NXCks56UdsdlNqRZAgI";
    String password = "CiSmokeTest#2026";

    boolean result = UserPasswordCommand.verify(password, storedHash);
    System.out.println("Password '" + password + "' matches hash: " + result);

    // Also test hash generation to see if it's different each time
    String hash1 = UserPasswordCommand.hash(password);
    String hash2 = UserPasswordCommand.hash(password);
    System.out.println("Hash 1: " + hash1);
    System.out.println("Hash 2: " + hash2);
    System.out.println("Hash 1 verifies: " + UserPasswordCommand.verify(password, hash1));
    System.out.println("Hash 2 verifies: " + UserPasswordCommand.verify(password, hash2));
  }
}
