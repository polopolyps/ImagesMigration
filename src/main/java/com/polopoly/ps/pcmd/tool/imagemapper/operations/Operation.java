package com.polopoly.ps.pcmd.tool.imagemapper.operations;

public interface Operation {
    Box apply(Box box);
    Box unApply(Box box);
}
