package com.polopoly.ps.pcmd.tool.imagemapper.operations;

import Jama.Matrix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.atex.onecms.image.Rectangle;

public class Box {
    private static final Comparator<Matrix> xComparator =
            (a, b) -> (int) (a.get(0, 0) - b.get(0, 0));
    private static final Comparator<Matrix> yComparator =
            (a, b) -> (int) (a.get(1, 0) - b.get(1, 0));
    private List<Matrix> vectors = new ArrayList<>();
    private List<Scramble> scrambles = new ArrayList<>();

    public Box(Matrix v0, Matrix v1, Matrix v2, Matrix v3) {
        vectors.add(v0);
        vectors.add(v1);
        vectors.add(v2);
        vectors.add(v3);
    }

    public Box(List<Matrix> vectors) {
        this.vectors = vectors;
    }

    public List<Matrix> getVectors() {
        return vectors;
    }

    public void setVectors(List<Matrix> vectors) {
        this.vectors = vectors;
    }

    public Matrix getVector(int i) {
        return vectors.get(i);
    }

    public void setVector(int i, Matrix vector) {
        vectors.set(i, vector);
    }

    public List<Scramble> getScrambles() {
        return scrambles;
    }

    public void setScrambles(List<Scramble> scrambles) {
        this.scrambles = scrambles;
    }

    public void addSubBox(Scramble subBox) {
        scrambles.add(subBox);
    }

    public double getHeight() {
        List<Integer> sortedIndexes = getSortedIndexes(vectors, yComparator);
        return getVector(sortedIndexes.get(sortedIndexes.size() - 1))
                .minus(getVector(sortedIndexes.get(0))).get(1, 0);
    }

    public double getWidth() {
        List<Integer> sortedIndexes = getSortedIndexes(vectors, xComparator);
        return getVector(sortedIndexes.get(sortedIndexes.size() - 1))
                .minus(getVector(sortedIndexes.get(0))).get(0, 0);
    }

    public void crop(double left, double right, double top, double bottom) {
//        Matrix startPoint = getStartPoint();
        List<Integer> sortedX =
                getSortedIndexes(vectors, xComparator);
        setVector(sortedX.get(0),
                  getVector(sortedX.get(0)).plus(new Matrix(new double[][] {{left}, {0}})));
        setVector(sortedX.get(1),
                  getVector(sortedX.get(1)).plus(new Matrix(new double[][] {{left}, {0}})));
        setVector(sortedX.get(2),
                  getVector(sortedX.get(2)).minus(new Matrix(new double[][] {{right}, {0}})));
        setVector(sortedX.get(3),
                  getVector(sortedX.get(3)).minus(new Matrix(new double[][] {{right}, {0}})));

        List<Integer> sortedY =
                getSortedIndexes(vectors, yComparator);
        setVector(sortedY.get(0),
                  getVector(sortedY.get(0)).plus(new Matrix(new double[][] {{0}, {top}})));
        setVector(sortedY.get(1),
                  getVector(sortedY.get(1)).plus(new Matrix(new double[][] {{0}, {top}})));
        setVector(sortedY.get(2),
                  getVector(sortedY.get(2)).minus(new Matrix(new double[][] {{0}, {bottom}})));
        setVector(sortedY.get(3),
                  getVector(sortedY.get(3)).minus(new Matrix(new double[][] {{0}, {bottom}})));
//        noNegative(startPoint);
    }

    public void addScramble(double left, double right, double top, double bottom, int level) {
        Matrix vector0 = new Matrix(new double[][] {{left}, {top}});
        Matrix vector1 = new Matrix(new double[][] {{left}, {getHeight() - bottom}});
        Matrix vector2 = new Matrix(new double[][] {{getWidth() - right}, {getHeight() - bottom}});
        Matrix vector3 = new Matrix(new double[][] {{getWidth() - right}, {top}});
        addSubBox(new Scramble(vector0.plus(getStartPoint()),
                               vector1.plus(getStartPoint()),
                               vector2.plus(getStartPoint()),
                               vector3.plus(getStartPoint()),
                               level));
    }

    //TODO negative y?
    public void noNegative(Matrix previousStartPoint) {
        List<Integer> sortedIndexes =
                getSortedIndexes(vectors, xComparator.thenComparing(yComparator));
        Matrix startPoint = vectors.get(sortedIndexes.get(0));
        Matrix difference = startPoint.minus(previousStartPoint);
        for (int i = sortedIndexes.size() - 1; i >= 0; i--) {
            Integer index = sortedIndexes.get(i);
            vectors.set(index, vectors.get(index)
                                      .minus(difference));
        }
        scrambles.forEach(box -> box.setVectors(box.getVectors()
                                                   .stream()
                                                   .peek(x -> String.format("0:(%f, %f)",
                                                                           x.get(0, 0),
                                                                           x.get(1, 0)))
                                                   .map(vector -> vector.minus(difference))
                                                   .peek(x -> String.format("1:(%f, %f)",
                                                                           x.get(0, 0),
                                                                           x.get(1, 0)))
                                                   .collect(Collectors.toList())));

    }

    public Matrix getStartPoint() {
        return vectors.get(getSortedIndexes(vectors, xComparator.thenComparing(yComparator)).get(0));
    }

    public void order() {
        vectors.sort(xComparator);
        vectors.subList(0, 2).sort(yComparator);
        vectors.subList(2, 4).sort((a, b) -> (int) (b.get(1, 0) - a.get(1, 0)));
        scrambles.forEach(Box::order);
    }

    @Override
    public String toString() {
        String output = vectors.stream()
                               .map(x -> String.format("(%f, %f)",
                                                       x.get(0, 0),
                                                       x.get(1, 0)))
                               .reduce((a, b) -> a + "\n" + b)
                               .orElse(null);
        output = output + '\n' + scrambles.stream()
                                          .map(Box::toString)
                                          .map(x -> x.replaceAll("\\n", "\n\t"))
                                          .map(x -> '\t' + x + '\n')
                                          .reduce((a, b) -> a + b)
                                          .orElse("");
        return output;
    }

    public static <T> List<Integer> getSortedIndexes(List<T> list, Comparator<T> comparator) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            indexes.add(i);
        }
        indexes.sort((a, b) -> comparator.compare(list.get(a), list.get(b)));
        return indexes;
    }

    public Rectangle getRectangle() {
        return new Rectangle((int) getStartPoint().get(0, 0),
                             (int) getStartPoint().get(1, 0),
                             (int) getWidth(),
                             (int) getHeight());
    }
}
