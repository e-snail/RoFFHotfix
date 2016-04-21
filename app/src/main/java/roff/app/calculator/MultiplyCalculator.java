package roff.app.calculator;

/**
 * Created by Wilbur Wu on 16/4/20.
 */
public class MultiplyCalculator implements ICalculator {

    @Override
    public int calculate(int value1, int value2) {
        return value1 + value2;
    }
}
