import com.simisinc.platform.application.UserPasswordCommand;

public class HashGen {
  public static void main(String[] args) {
    String hash = UserPasswordCommand.hash("test");
    System.out.println(hash);
  }
}
