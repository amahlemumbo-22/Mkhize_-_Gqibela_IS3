public class PasswordValidator {

    public static boolean isValidPassword(String password) {
        return password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
    }

    public static final String RULES =
            "Password must be at least 8 characters and include an uppercase letter, "
          + "a lowercase letter, a digit and a special character.";
}
