// "Replace with count()" "true"

public class Main {
  public long test(String[] array) {
    long longStrings = 0;
    for(String str : a<caret>rray) {
      String trimmed = str.trim();
      if(trimmed.length() > 10) {
        longStrings = longStrings + 1;
      }
    }
    return longStrings;
  }
}