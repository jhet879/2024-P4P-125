//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.example;

import java.util.EmptyStackException;

public class Stack<T> {
    private int capacity = 10;
    private int pointer = 0;
    private T[] objects;

    public Stack() {
        this.objects = (Object[])(new Object[this.capacity]);
    }

    public void push(T o) {
        if (this.pointer >= this.capacity) {
            throw new RuntimeException("Stack exceeded capacity!");
        } else {
            this.objects[this.pointer++] = o;
        }
    }

    public T pop() {
        if (this.pointer <= 0) {
            throw new EmptyStackException();
        } else {
            return this.objects[--this.pointer];
        }
    }

    public boolean isEmpty() {
        return this.pointer <= 0;
    }
}
