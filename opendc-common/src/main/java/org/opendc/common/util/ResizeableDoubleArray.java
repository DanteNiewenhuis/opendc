package org.opendc.common.util;

public class ResizeableDoubleArray {
    private double[] elements;
    private int size;

    public ResizeableDoubleArray() {
        this.elements = new double[10];
        this.size = 0;
    }

    public ResizeableDoubleArray(int size) {
        this.elements = new double[size];
        this.size = 0;
    }

    public void add(double element) {
        if (size == elements.length) {
            grow();
        }
        elements[size++] = element;
    }

    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: "+size;
    }


    private void rangeCheckForAdd(int index) {
        if (index > size || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    public void add(int index, double element) {
        rangeCheckForAdd(index);
        final int s;
        double[] elementData;
        if ((s = this.size) == (elementData = this.elements).length)
            this.grow();
        System.arraycopy(elementData, index,
            elementData, index + 1,
            s - index);
        elementData[index] = element;
        size = s + 1;
    }

    public void addFirst(double element) {
        this.add(0, element);
    }

    public double get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        return elements[index];
    }

    public void set(int index, double value) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        elements[index] = value;
    }

    public int size() {
        return size;
    }

    private void grow() {
        int newSize = elements.length  >> 1;

        double[] newElements = new double[newSize];
        System.arraycopy(elements, 0, newElements, 0, elements.length);
        elements = newElements;
    }
}
