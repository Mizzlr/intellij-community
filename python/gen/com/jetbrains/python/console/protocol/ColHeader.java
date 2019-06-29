/**
 * Autogenerated by Thrift Compiler (0.11.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.jetbrains.python.console.protocol;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.11.0)", date = "2018-08-07")
public class ColHeader implements org.apache.thrift.TBase<ColHeader, ColHeader._Fields>, java.io.Serializable, Cloneable, Comparable<ColHeader> {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("ColHeader");

  private static final org.apache.thrift.protocol.TField LABEL_FIELD_DESC = new org.apache.thrift.protocol.TField("label", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField TYPE_FIELD_DESC = new org.apache.thrift.protocol.TField("type", org.apache.thrift.protocol.TType.STRING, (short)2);
  private static final org.apache.thrift.protocol.TField FORMAT_FIELD_DESC = new org.apache.thrift.protocol.TField("format", org.apache.thrift.protocol.TType.STRING, (short)3);
  private static final org.apache.thrift.protocol.TField MAX_FIELD_DESC = new org.apache.thrift.protocol.TField("max", org.apache.thrift.protocol.TType.STRING, (short)4);
  private static final org.apache.thrift.protocol.TField MIN_FIELD_DESC = new org.apache.thrift.protocol.TField("min", org.apache.thrift.protocol.TType.STRING, (short)5);

  private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY = new ColHeaderStandardSchemeFactory();
  private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY = new ColHeaderTupleSchemeFactory();

  public java.lang.String label; // required
  public java.lang.String type; // required
  public java.lang.String format; // required
  public java.lang.String max; // required
  public java.lang.String min; // required

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    LABEL((short)1, "label"),
    TYPE((short)2, "type"),
    FORMAT((short)3, "format"),
    MAX((short)4, "max"),
    MIN((short)5, "min");

    private static final java.util.Map<java.lang.String, _Fields> byName = new java.util.HashMap<java.lang.String, _Fields>();

    static {
      for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // LABEL
          return LABEL;
        case 2: // TYPE
          return TYPE;
        case 3: // FORMAT
          return FORMAT;
        case 4: // MAX
          return MAX;
        case 5: // MIN
          return MIN;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(java.lang.String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final java.lang.String _fieldName;

    _Fields(short thriftId, java.lang.String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public java.lang.String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.LABEL, new org.apache.thrift.meta_data.FieldMetaData("label", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.TYPE, new org.apache.thrift.meta_data.FieldMetaData("type", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.FORMAT, new org.apache.thrift.meta_data.FieldMetaData("format", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.MAX, new org.apache.thrift.meta_data.FieldMetaData("max", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.MIN, new org.apache.thrift.meta_data.FieldMetaData("min", org.apache.thrift.TFieldRequirementType.DEFAULT, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(ColHeader.class, metaDataMap);
  }

  public ColHeader() {
  }

  public ColHeader(
    java.lang.String label,
    java.lang.String type,
    java.lang.String format,
    java.lang.String max,
    java.lang.String min)
  {
    this();
    this.label = label;
    this.type = type;
    this.format = format;
    this.max = max;
    this.min = min;
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public ColHeader(ColHeader other) {
    if (other.isSetLabel()) {
      this.label = other.label;
    }
    if (other.isSetType()) {
      this.type = other.type;
    }
    if (other.isSetFormat()) {
      this.format = other.format;
    }
    if (other.isSetMax()) {
      this.max = other.max;
    }
    if (other.isSetMin()) {
      this.min = other.min;
    }
  }

  public ColHeader deepCopy() {
    return new ColHeader(this);
  }

  @Override
  public void clear() {
    this.label = null;
    this.type = null;
    this.format = null;
    this.max = null;
    this.min = null;
  }

  public java.lang.String getLabel() {
    return this.label;
  }

  public ColHeader setLabel(java.lang.String label) {
    this.label = label;
    return this;
  }

  public void unsetLabel() {
    this.label = null;
  }

  /** Returns true if field label is set (has been assigned a value) and false otherwise */
  public boolean isSetLabel() {
    return this.label != null;
  }

  public void setLabelIsSet(boolean value) {
    if (!value) {
      this.label = null;
    }
  }

  public java.lang.String getType() {
    return this.type;
  }

  public ColHeader setType(java.lang.String type) {
    this.type = type;
    return this;
  }

  public void unsetType() {
    this.type = null;
  }

  /** Returns true if field type is set (has been assigned a value) and false otherwise */
  public boolean isSetType() {
    return this.type != null;
  }

  public void setTypeIsSet(boolean value) {
    if (!value) {
      this.type = null;
    }
  }

  public java.lang.String getFormat() {
    return this.format;
  }

  public ColHeader setFormat(java.lang.String format) {
    this.format = format;
    return this;
  }

  public void unsetFormat() {
    this.format = null;
  }

  /** Returns true if field format is set (has been assigned a value) and false otherwise */
  public boolean isSetFormat() {
    return this.format != null;
  }

  public void setFormatIsSet(boolean value) {
    if (!value) {
      this.format = null;
    }
  }

  public java.lang.String getMax() {
    return this.max;
  }

  public ColHeader setMax(java.lang.String max) {
    this.max = max;
    return this;
  }

  public void unsetMax() {
    this.max = null;
  }

  /** Returns true if field max is set (has been assigned a value) and false otherwise */
  public boolean isSetMax() {
    return this.max != null;
  }

  public void setMaxIsSet(boolean value) {
    if (!value) {
      this.max = null;
    }
  }

  public java.lang.String getMin() {
    return this.min;
  }

  public ColHeader setMin(java.lang.String min) {
    this.min = min;
    return this;
  }

  public void unsetMin() {
    this.min = null;
  }

  /** Returns true if field min is set (has been assigned a value) and false otherwise */
  public boolean isSetMin() {
    return this.min != null;
  }

  public void setMinIsSet(boolean value) {
    if (!value) {
      this.min = null;
    }
  }

  public void setFieldValue(_Fields field, java.lang.Object value) {
    switch (field) {
    case LABEL:
      if (value == null) {
        unsetLabel();
      } else {
        setLabel((java.lang.String)value);
      }
      break;

    case TYPE:
      if (value == null) {
        unsetType();
      } else {
        setType((java.lang.String)value);
      }
      break;

    case FORMAT:
      if (value == null) {
        unsetFormat();
      } else {
        setFormat((java.lang.String)value);
      }
      break;

    case MAX:
      if (value == null) {
        unsetMax();
      } else {
        setMax((java.lang.String)value);
      }
      break;

    case MIN:
      if (value == null) {
        unsetMin();
      } else {
        setMin((java.lang.String)value);
      }
      break;

    }
  }

  public java.lang.Object getFieldValue(_Fields field) {
    switch (field) {
    case LABEL:
      return getLabel();

    case TYPE:
      return getType();

    case FORMAT:
      return getFormat();

    case MAX:
      return getMax();

    case MIN:
      return getMin();

    }
    throw new java.lang.IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new java.lang.IllegalArgumentException();
    }

    switch (field) {
    case LABEL:
      return isSetLabel();
    case TYPE:
      return isSetType();
    case FORMAT:
      return isSetFormat();
    case MAX:
      return isSetMax();
    case MIN:
      return isSetMin();
    }
    throw new java.lang.IllegalStateException();
  }

  @Override
  public boolean equals(java.lang.Object that) {
    if (that == null)
      return false;
    if (that instanceof ColHeader)
      return this.equals((ColHeader)that);
    return false;
  }

  public boolean equals(ColHeader that) {
    if (that == null)
      return false;
    if (this == that)
      return true;

    boolean this_present_label = true && this.isSetLabel();
    boolean that_present_label = true && that.isSetLabel();
    if (this_present_label || that_present_label) {
      if (!(this_present_label && that_present_label))
        return false;
      if (!this.label.equals(that.label))
        return false;
    }

    boolean this_present_type = true && this.isSetType();
    boolean that_present_type = true && that.isSetType();
    if (this_present_type || that_present_type) {
      if (!(this_present_type && that_present_type))
        return false;
      if (!this.type.equals(that.type))
        return false;
    }

    boolean this_present_format = true && this.isSetFormat();
    boolean that_present_format = true && that.isSetFormat();
    if (this_present_format || that_present_format) {
      if (!(this_present_format && that_present_format))
        return false;
      if (!this.format.equals(that.format))
        return false;
    }

    boolean this_present_max = true && this.isSetMax();
    boolean that_present_max = true && that.isSetMax();
    if (this_present_max || that_present_max) {
      if (!(this_present_max && that_present_max))
        return false;
      if (!this.max.equals(that.max))
        return false;
    }

    boolean this_present_min = true && this.isSetMin();
    boolean that_present_min = true && that.isSetMin();
    if (this_present_min || that_present_min) {
      if (!(this_present_min && that_present_min))
        return false;
      if (!this.min.equals(that.min))
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int hashCode = 1;

    hashCode = hashCode * 8191 + ((isSetLabel()) ? 131071 : 524287);
    if (isSetLabel())
      hashCode = hashCode * 8191 + label.hashCode();

    hashCode = hashCode * 8191 + ((isSetType()) ? 131071 : 524287);
    if (isSetType())
      hashCode = hashCode * 8191 + type.hashCode();

    hashCode = hashCode * 8191 + ((isSetFormat()) ? 131071 : 524287);
    if (isSetFormat())
      hashCode = hashCode * 8191 + format.hashCode();

    hashCode = hashCode * 8191 + ((isSetMax()) ? 131071 : 524287);
    if (isSetMax())
      hashCode = hashCode * 8191 + max.hashCode();

    hashCode = hashCode * 8191 + ((isSetMin()) ? 131071 : 524287);
    if (isSetMin())
      hashCode = hashCode * 8191 + min.hashCode();

    return hashCode;
  }

  @Override
  public int compareTo(ColHeader other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;

    lastComparison = java.lang.Boolean.valueOf(isSetLabel()).compareTo(other.isSetLabel());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetLabel()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.label, other.label);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetType()).compareTo(other.isSetType());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetType()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.type, other.type);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetFormat()).compareTo(other.isSetFormat());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetFormat()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.format, other.format);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetMax()).compareTo(other.isSetMax());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMax()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.max, other.max);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = java.lang.Boolean.valueOf(isSetMin()).compareTo(other.isSetMin());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetMin()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.min, other.min);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
    scheme(iprot).read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
    scheme(oprot).write(oprot, this);
  }

  @Override
  public java.lang.String toString() {
    java.lang.StringBuilder sb = new java.lang.StringBuilder("ColHeader(");
    boolean first = true;

    sb.append("label:");
    if (this.label == null) {
      sb.append("null");
    } else {
      sb.append(this.label);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("type:");
    if (this.type == null) {
      sb.append("null");
    } else {
      sb.append(this.type);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("format:");
    if (this.format == null) {
      sb.append("null");
    } else {
      sb.append(this.format);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("max:");
    if (this.max == null) {
      sb.append("null");
    } else {
      sb.append(this.max);
    }
    first = false;
    if (!first) sb.append(", ");
    sb.append("min:");
    if (this.min == null) {
      sb.append("null");
    } else {
      sb.append(this.min);
    }
    first = false;
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws org.apache.thrift.TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
    try {
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (org.apache.thrift.TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class ColHeaderStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public ColHeaderStandardScheme getScheme() {
      return new ColHeaderStandardScheme();
    }
  }

  private static class ColHeaderStandardScheme extends org.apache.thrift.scheme.StandardScheme<ColHeader> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, ColHeader struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // LABEL
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.label = iprot.readString();
              struct.setLabelIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // TYPE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.type = iprot.readString();
              struct.setTypeIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // FORMAT
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.format = iprot.readString();
              struct.setFormatIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // MAX
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.max = iprot.readString();
              struct.setMaxIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // MIN
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.min = iprot.readString();
              struct.setMinIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, ColHeader struct) throws org.apache.thrift.TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.label != null) {
        oprot.writeFieldBegin(LABEL_FIELD_DESC);
        oprot.writeString(struct.label);
        oprot.writeFieldEnd();
      }
      if (struct.type != null) {
        oprot.writeFieldBegin(TYPE_FIELD_DESC);
        oprot.writeString(struct.type);
        oprot.writeFieldEnd();
      }
      if (struct.format != null) {
        oprot.writeFieldBegin(FORMAT_FIELD_DESC);
        oprot.writeString(struct.format);
        oprot.writeFieldEnd();
      }
      if (struct.max != null) {
        oprot.writeFieldBegin(MAX_FIELD_DESC);
        oprot.writeString(struct.max);
        oprot.writeFieldEnd();
      }
      if (struct.min != null) {
        oprot.writeFieldBegin(MIN_FIELD_DESC);
        oprot.writeString(struct.min);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class ColHeaderTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
    public ColHeaderTupleScheme getScheme() {
      return new ColHeaderTupleScheme();
    }
  }

  private static class ColHeaderTupleScheme extends org.apache.thrift.scheme.TupleScheme<ColHeader> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, ColHeader struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet optionals = new java.util.BitSet();
      if (struct.isSetLabel()) {
        optionals.set(0);
      }
      if (struct.isSetType()) {
        optionals.set(1);
      }
      if (struct.isSetFormat()) {
        optionals.set(2);
      }
      if (struct.isSetMax()) {
        optionals.set(3);
      }
      if (struct.isSetMin()) {
        optionals.set(4);
      }
      oprot.writeBitSet(optionals, 5);
      if (struct.isSetLabel()) {
        oprot.writeString(struct.label);
      }
      if (struct.isSetType()) {
        oprot.writeString(struct.type);
      }
      if (struct.isSetFormat()) {
        oprot.writeString(struct.format);
      }
      if (struct.isSetMax()) {
        oprot.writeString(struct.max);
      }
      if (struct.isSetMin()) {
        oprot.writeString(struct.min);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, ColHeader struct) throws org.apache.thrift.TException {
      org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
      java.util.BitSet incoming = iprot.readBitSet(5);
      if (incoming.get(0)) {
        struct.label = iprot.readString();
        struct.setLabelIsSet(true);
      }
      if (incoming.get(1)) {
        struct.type = iprot.readString();
        struct.setTypeIsSet(true);
      }
      if (incoming.get(2)) {
        struct.format = iprot.readString();
        struct.setFormatIsSet(true);
      }
      if (incoming.get(3)) {
        struct.max = iprot.readString();
        struct.setMaxIsSet(true);
      }
      if (incoming.get(4)) {
        struct.min = iprot.readString();
        struct.setMinIsSet(true);
      }
    }
  }

  private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
    return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY : TUPLE_SCHEME_FACTORY).getScheme();
  }
}

