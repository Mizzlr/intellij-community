// "Copy 'switch' branch" "true"
class C {
    void foo(int n) {
        String s = "";
        switch (n) {
            case 1:
            <caret>default:
            case 2:
                s = "x";
                break;
        }
    }
}