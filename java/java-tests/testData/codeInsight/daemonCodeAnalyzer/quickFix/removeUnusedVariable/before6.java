// "Remove variable 'i'" "true"
import java.io.*;

class a {
    private int run() {
        int <caret>i = 0;
        int j;
        i++;
        int k = 9;
        i=(i=(k=9)==0 ? k=8 : 0);
        i = 9;
        if ((i=3)==0) i=0;
        else return i=(k);
        return i=i=0;
    }
}
