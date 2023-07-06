const { TypeObj, ValueObj } = require("sigmastate-js");

function testRange(factory, min, max) {
  expect(factory(max).data).toEqual(max);
  expect(() => factory(max + 1).data).toThrow();
  expect(factory(-1).data).toEqual(-1);
  expect(factory(min).data).toEqual(min);
  expect(() => factory(min - 1).data).toThrow();
}

describe("Smoke tests for Values", () => {
  it("Should create values of primitive types", () => {
    expect(ValueObj.ofByte(0).data).toEqual(0);
    expect(ValueObj.ofByte(0).tpe).toEqual(TypeObj.Byte);
    testRange(function(v) { return ValueObj.ofByte(v); }, -128, 127);
    testRange(function(v) { return ValueObj.ofShort(v); }, -32768, 32767);
    testRange(function(v) { return ValueObj.ofInt(v); }, -0x7FFFFFFF - 1, 0x7FFFFFFF);
    testRange(function(v) { return ValueObj.ofLong(v); }, -0x8000000000000000n, 0x7fffffffffffffffn);
  });

  it("Should create values of complex types", () => {
    let pair = ValueObj.pairOf(ValueObj.ofByte(10), ValueObj.ofLong(20n));
    expect(pair.data).toEqual([10, 20n]);
    expect(pair.tpe.name).toEqual("(Byte, Long)");

    let coll = ValueObj.collOf([-10, 0, 10], TypeObj.Byte)
    expect(coll.tpe.name).toEqual("Coll[Byte]");
  });

  let longHex = "05e012";
  let bigIntHex = "060900fffffffffffffffe";
  let groupElementHex = "0702c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5";
  let sigmaPropHex = "08cd0297c44a12f4eb99a85d298fa3ba829b5b42b9f63798c980ece801cc663cc5fc9e";
  let avlTreeHex = "643100d2e101ff01fc047c7f6f00ff80129df69a5090012f01ffca99f5bfff0c803601800100";
  let collHex = "1a0203010203020a14";
  let pairHex = "3e050a28"

  it("Long Value.toHex", () => {
    let v = ValueObj.ofLong(1200n)
    expect(v.toHex()).toEqual(longHex)
  });

  it("BigInt Value.toHex", () => {
    let v = ValueObj.ofBigInt(0xfffffffffffffffen)
    expect(v.toHex()).toEqual(bigIntHex)
  });

  it("GroupElement Value.toHex", () => {
    let v = ValueObj.fromHex(groupElementHex)
    expect(v.toHex()).toEqual(groupElementHex)
  });

  it("SigmaProp Value.toHex", () => {
    let v = ValueObj.fromHex(sigmaPropHex)
    expect(v.toHex()).toEqual(sigmaPropHex)
  });

  it("AvlTree Value.toHex", () => {
    let v = ValueObj.fromHex(avlTreeHex)
    expect(v.toHex()).toEqual(avlTreeHex)
  });

  it("Coll Value.toHex", () => {
    let arr = [ [1, 2, 3], [10, 20] ]
    let t = TypeObj.collType(TypeObj.Byte)
    let collV = ValueObj.collOf(arr, t)

    expect(collV.tpe.name).toEqual("Coll[Coll[Byte]]");
    expect(collV.toHex()).toEqual(collHex)
  });

  it("Pair Value.toHex", () => {
    let fst = ValueObj.ofByte(10)
    let snd = ValueObj.ofLong(20)
    let pair = ValueObj.pairOf(fst, snd)
    expect(pair.tpe.name).toEqual("(Byte, Long)");
    expect(pair.toHex()).toEqual(pairHex)
  });

  it("Long Value.fromHex", () => {
    let v = ValueObj.fromHex(longHex)
    expect(v.data).toEqual(1200n)
    expect(v.tpe.name).toEqual("Long")
  });

  it("Coll Value.fromHex", () => {
    let coll = ValueObj.fromHex(collHex)
    expect(coll.tpe.name).toEqual("Coll[Coll[Byte]]");
    expect(coll.toHex()).toEqual(collHex)
  });

  it("Pair Value.fromHex", () => {
    let p = ValueObj.fromHex(pairHex)
    expect(p.tpe.name).toEqual("(Byte, Long)");
    expect(p.toHex()).toEqual(pairHex)
  });
});
