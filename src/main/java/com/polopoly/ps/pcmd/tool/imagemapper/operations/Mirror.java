package com.polopoly.ps.pcmd.tool.imagemapper.operations;

import Jama.Matrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Mirror implements Operation {
    public static final Mirror VERTICAL = new Mirror(Direction.VERTICAL);
    public static final Mirror HORIZONTAL = new Mirror(Direction.HORIZONTAL);

    private final Direction direction;

    public Mirror(Direction direction) {
        this.direction = direction;
    }

    @Override
    public Box apply(Box box) {
        Box output = mirror(box);
        output.setScrambles(mirrorSubBoxes(box));
        output.order();
        return output;
    }

    @Override
    public Box unApply(Box box) {
        return apply(box);
    }

    public Direction getDirection() {
        return direction;
    }

    private Box mirror(Box box) {
        List<Matrix> vectors = box.getVectors();
        if (direction == Direction.VERTICAL) {
            List<Integer> indexes = Box.getSortedIndexes(vectors,
                                                     (a, b) -> (int) (a.get(0, 0) - b.get(0, 0)));
            Collections.swap(vectors, indexes.get(0), indexes.get(1));
            Collections.swap(vectors, indexes.get(2), indexes.get(3));
        } else {
            List<Integer> indexes = Box.getSortedIndexes(vectors,
                                                     (a, b) -> (int) (a.get(1, 0) - b.get(1, 0)));
            Collections.swap(vectors, indexes.get(0), indexes.get(1));
            Collections.swap(vectors, indexes.get(2), indexes.get(3));
        }
        return new Box(vectors);
    }

    private List<Scramble> mirrorSubBoxes(Box box) {
        Stream<List<Matrix>> subBoxVectorStream = box.getScrambles()
                                                     .stream()
                                                     .map(Box::getVectors);
        List<Scramble> scrambles = new ArrayList<>();
        for (Scramble scramble : box.getScrambles()) {
            Stream<Matrix> stream = scramble.getVectors()
                                            .stream();
            List<Matrix> vectors;
            if (direction == Direction.VERTICAL) {
                vectors = stream
                        .map(vector -> {
                            vector.set(1,
                                       0,
                                       box.getHeight() -
                                       vector.get(1, 0));
                            return vector;
                        }).collect(Collectors.toList());

            } else {
                vectors = stream
                        .map(vector -> {
                            vector.set(0,
                                       0,
                                       box.getWidth() -
                                       vector.get(0, 0));
                            return vector;
                        }).collect(Collectors.toList());
            }
            scrambles.add(new Scramble(vectors, scramble.getLevel()));
        }
        return scrambles;
    }

    private List<Matrix> moveSubBoxVector(List<Matrix> vectorList,
                                          Function<Matrix, Matrix> mover) {
        return vectorList.stream().map(mover).collect(Collectors.toList());
    }

    public enum Direction {
        VERTICAL, HORIZONTAL
    }
}
