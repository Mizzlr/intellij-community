public class ConstConfig {
    int test(boolean aaaaaaaa) {
        Boolean b;
        if ((b = switch (0) {
            case 1: {
                break aaaa<caret>;
            }
            default: break false;
        }));
    }
}