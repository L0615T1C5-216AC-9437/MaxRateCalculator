package maximus;

public class matrix {
    //source https://www.geeksforgeeks.org/program-for-gauss-jordan-elimination-method/

    static int PerformOperation(float[][] a, int n) {
        int i, j, k, c, flag = 0;

        for (i = 0; i < n; i++) {
            if (a[i][i] == 0) {
                c = 1;
                while (i + c < n && a[i + c][i] == 0) c++;
                if (i + c == n) {
                    flag = 1;
                    break;
                }
                for (j = i, k = 0; k <= n; k++) {
                    float temp =a[j][k];
                    a[j][k] = a[j+c][k];
                    a[j+c][k] = temp;
                }
            }

            for (j = 0; j < n; j++) {
                if (i != j) {
                    float p = a[j][i] / a[i][i];

                    for (k = 0; k <= n; k++) {
                        a[j][k] = a[j][k] - (a[i][k]) * p;
                    }
                }
            }
        }

        return flag;
    }

    static int CheckConsistency(float[][] a, int n) {
        int i, j;
        float sum;
        int flag = 3;
        //check
        for (i = 0; i < n; i++) {
            sum = 0;
            for (j = 0; j < n; j++) sum += a[i][j];
            if (sum == a[i][j]) {
                flag = 2;
            }
        }
        return flag;
    }

    public static float[] calculate(float[][] a) throws Exception {
        int n = a.length;
        int flag = PerformOperation(a, n);
        if (flag == 1) {
            flag = CheckConsistency(a, n);
        }
        if (flag > 1) throw new Exception("bad"); //should never happen
        //get perfect ratio (not necessarily most optimal)
        float[] out = new float[n];
        for (int i = 0; i < n; i++)
            out[i] = a[i][n] / a[i][i];
        return out;
    }
}