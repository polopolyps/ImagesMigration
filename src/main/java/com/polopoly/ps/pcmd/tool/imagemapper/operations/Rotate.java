package com.polopoly.ps.pcmd.tool.imagemapper.operations;

import Jama.Matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Rotate implements Operation {

    public static final Rotate DEGREES_90 = new Rotate(Math.PI / 2);
    public static final Rotate DEGREES_180 = new Rotate(Math.PI);
    public static final Rotate DEGREES_270 = new Rotate(Math.PI + Math.PI / 2);

    private final double theta;

    public Rotate(double theta) {
        this.theta = theta;
    }

    @Override
    public Box apply(Box box) {
        Box rotate = rotate(box, theta);
        rotate.noNegative(box.getStartPoint());
        return rotate;
    }

    @Override
    public Box unApply(Box box) {
        Box rotate = rotate(box, -theta);
        rotate.noNegative(box.getStartPoint());
        return rotate;

    }

    public double getTheta() {
        return theta;
    }

    private Box rotate(Box box, double theta) {
        List<Matrix> rotated = box.getVectors()
                                  .stream()
                                  .map(matrix -> rotationMatrix(theta).times(matrix))
                                  .collect(Collectors.toList());
        Box output = new Box(rotated);
        output.setScrambles(rotateScrambles(box.getScrambles(), theta));
//        output.noNegative(box.getStartPoint());
        output.order();
        return output;
    }

    private List<Scramble> rotateScrambles(List<Scramble> scrambles, double theta) {
        List<Scramble> output = new ArrayList<>();
        for (Scramble scramble : scrambles) {
            List<Matrix> vectors = scramble.getVectors()
                                           .stream()
                                           .map(matrix -> rotationMatrix(theta).times(matrix))
                                           .collect(Collectors.toList());
            output.add(new Scramble(vectors, scramble.getLevel()));
        }
        return output;
    }

    private Matrix rotationMatrix(double theta) {
        double sin = Math.sin(theta);
        double cos = Math.cos(theta);

        return new Matrix(new double[][] {{cos, -sin},
                                          {sin, cos}});
    }
}
