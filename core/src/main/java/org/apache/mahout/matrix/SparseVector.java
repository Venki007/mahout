/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.matrix;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;

/**
 * Implements vector that only stores non-zero doubles
 */
public class SparseVector extends AbstractVector {

  /** For serialization purposes only. */
  public SparseVector() {
  }

  private Map<Integer, Double> values;

  private int cardinality;

  public static boolean optimizeTimes = true;

  public SparseVector(int cardinality) {
    values = new HashMap<Integer, Double>();
    this.cardinality = cardinality;
  }

  @Override
  protected Matrix matrixLike(int rows, int columns) {
    int[] cardinality = { rows, columns };
    return new SparseRowMatrix(cardinality);
  }

  @Override
  public WritableComparable<?> asWritableComparable() {
    String out = asFormatString();
    return new Text(out);
  }

  @Override
  public int cardinality() {
    return cardinality;
  }

  @Override
  public SparseVector copy() {
    SparseVector result = like();
     for (Map.Entry<Integer, Double> entry : values.entrySet()) {
      result.setQuick(entry.getKey(), entry.getValue());
    }
    return result;
  }

  @Override
  public double getQuick(int index) {
    Double value = values.get(index);
    return value == null ? 0.0 : value;
  }

  @Override
  public void setQuick(int index, double value) {
    if (value == 0.0)
      values.remove(index);
    else
      values.put(index, value);
  }

  @Override
  public int size() {
    return values.size();
  }

  @Override
  public double[] toArray() {
    double[] result = new double[cardinality];
    for (Map.Entry<Integer, Double> entry : values.entrySet()) {
      result[entry.getKey()] = entry.getValue();
    }
    return result;
  }

  @Override
  public Vector viewPart(int offset, int length) {
    if (length > cardinality)
      throw new CardinalityException();
    if (offset < 0 || offset + length > cardinality)
      throw new IndexException();
    return new VectorView(this, offset, length);
  }

  @Override
  public boolean haveSharedCells(Vector other) {
    if (other instanceof SparseVector)
      return other == this;
    else
      return other.haveSharedCells(this);
  }

  @Override
  public SparseVector like() {
    SparseVector sparseVector = new SparseVector(cardinality);
    sparseVector.setLabelBindings(getLabelBindings());
    return sparseVector;
  }

  @Override
  public Vector like(int newCardinality) {
    SparseVector sparseVector = new SparseVector(newCardinality);
    sparseVector.setLabelBindings(getLabelBindings());
    return sparseVector;
  }

  @Override
  public java.util.Iterator<Vector.Element> iterator() {
    return new Iterator();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Vector)) return false;

    Vector that = (Vector) o;
    if (this.cardinality() != that.cardinality()) return false;

    if (that instanceof SparseVector) {
      return (values == null ? ((SparseVector) that).values == null : values.equals(((SparseVector) that).values));
    } else {
      return equivalent(this, that);
    }

  }



  @Override
  public int hashCode() {
    int result = (values != null ? values.hashCode() : 0);
    result = 31 * result + cardinality;
    return result;
  }

  private class Iterator implements java.util.Iterator<Vector.Element> {
    private final java.util.Iterator<Map.Entry<Integer, Double>> it;

    Iterator() {
      it = values.entrySet().iterator();
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public Element next() {
      return new Element(it.next().getKey());
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public double zSum() {
    double result = 0.0;
    for (Double value : values.values()) {
      result += value;
    }
    return result;
  }

  @Override
  public double dot(Vector x) {
    if (cardinality() != x.cardinality())
      throw new CardinalityException();
    double result = 0.0;
    for (Map.Entry<Integer, Double> entry : values.entrySet()) {
      result += entry.getValue() * x.getQuick(entry.getKey());
    }
    return result;
  }

  @Override
  public void write(DataOutput dataOutput) throws IOException {
    dataOutput.writeInt(cardinality());
    dataOutput.writeInt(size());
    for (Vector.Element element : this) {
      if (element.get() != 0.0d) {
        dataOutput.writeInt(element.index());
        dataOutput.writeDouble(element.get());
      }
    }
  }

  @Override
  public void readFields(DataInput dataInput) throws IOException {
    int cardinality = dataInput.readInt();
    int size = dataInput.readInt();
    Map<Integer, Double> values = new HashMap<Integer, Double>(size);
    for (int i = 0; i < size; i++) {
      values.put(dataInput.readInt(), dataInput.readDouble());
    }
    this.cardinality = cardinality;
    this.values = values;
  }

  @Override
  public Vector times(double x) {
    Vector result;
    if (optimizeTimes) {
      result = like();
      for (Vector.Element element : this) {
        double value = element.get();
        int index = element.index();
        result.setQuick(index, value * x);
      }
    } else {
      result = copy();
      for (int i = 0; i < result.cardinality(); i++)
        result.setQuick(i, getQuick(i) * x);
    }
    return result;
  }

  @Override
  public Vector times(Vector x) {
    if (cardinality() != x.cardinality())
      throw new CardinalityException();
    Vector result;
    if (optimizeTimes) {
      result = like();
      for (Vector.Element element : this) {
        double value = element.get();
        int index = element.index();
        result.setQuick(index, value * x.getQuick(index));
      }
    } else {
      result = copy();
      for (int i = 0; i < result.cardinality(); i++)
        result.setQuick(i, getQuick(i) * x.getQuick(i));
    }
    return result;
  }

}
