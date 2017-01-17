package com.polopoly.ps.pcmd.tool.imagemapper.operations;

import Jama.Matrix;

import java.util.List;

public class Scramble extends Box {
    private int level;

    public Scramble(Matrix v0, Matrix v1, Matrix v2, Matrix v3, int level) {
        super(v0, v1, v2, v3);
        this.level = level;
    }

    public Scramble(List<Matrix> vectors, int level) {
        super(vectors);
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
