function factorize(n) {
  const factors = [];
  for (const p of [4, 2, 3, 5]) {
    while (n % p === 0) {
      factors.push(p);
      n /= p;
    }
  }
  let p = 7;
  while (n > 1) {
    while (n % p === 0) {
      factors.push(p);
      n /= p;
    }
    p += 2;
  }
  return factors;
}

export class FFT {
  constructor(n) {
    this.n = n;
    this.factors = factorize(n);
    this.cos = new Float64Array(n);
    this.sin = new Float64Array(n);
    for (let k = 0; k < n; k++) {
      const a = (-2 * Math.PI * k) / n;
      this.cos[k] = Math.cos(a);
      this.sin[k] = Math.sin(a);
    }
    const maxP = Math.max(...this.factors, 1);
    this.tRe = new Float64Array(maxP);
    this.tIm = new Float64Array(maxP);
    this.zeroIm = new Float64Array(n);
    this.bufRe = new Float64Array(n);
    this.bufIm = new Float64Array(n);
  }

  transform(inRe, inIm, outRe, outIm, inverse = false) {
    this.sign = inverse ? -1 : 1;
    this.run(outRe, outIm, 0, inRe, inIm, 0, 1, this.n, 0);
  }

  run(oRe, oIm, oOff, iRe, iIm, iOff, stride, n, level) {
    if (n === 1) {
      oRe[oOff] = iRe[iOff];
      oIm[oOff] = iIm[iOff];
      return;
    }
    const p = this.factors[level];
    const m = n / p;
    for (let q = 0; q < p; q++) {
      this.run(oRe, oIm, oOff + q * m, iRe, iIm, iOff + q * stride, stride * p, m, level + 1);
    }
    const N = this.n;
    const cos = this.cos;
    const sin = this.sin;
    const sign = this.sign;
    const tRe = this.tRe;
    const tIm = this.tIm;
    const step = N / n;
    for (let k = 0; k < m; k++) {
      for (let q = 0; q < p; q++) {
        tRe[q] = oRe[oOff + q * m + k];
        tIm[q] = oIm[oOff + q * m + k];
      }
      for (let u = 0; u < p; u++) {
        const idx = k + u * m;
        let sr = tRe[0];
        let si = tIm[0];
        for (let q = 1; q < p; q++) {
          const e = ((q * idx) % n) * step;
          const wr = cos[e];
          const wi = sin[e] * sign;
          sr += tRe[q] * wr - tIm[q] * wi;
          si += tRe[q] * wi + tIm[q] * wr;
        }
        oRe[oOff + idx] = sr;
        oIm[oOff + idx] = si;
      }
    }
  }

  realForward(input, outRe, outIm) {
    const n = this.n;
    this.transform(input, this.zeroIm, this.bufRe, this.bufIm, false);
    const half = n / 2;
    for (let k = 0; k <= half; k++) {
      outRe[k] = this.bufRe[k];
      outIm[k] = this.bufIm[k];
    }
  }

  realInverse(inRe, inIm, output) {
    const n = this.n;
    const half = n / 2;
    const re = this.bufRe;
    const im = this.bufIm;
    re[0] = inRe[0];
    im[0] = 0;
    re[half] = inRe[half];
    im[half] = 0;
    for (let k = 1; k < half; k++) {
      re[k] = inRe[k];
      im[k] = inIm[k];
      re[n - k] = inRe[k];
      im[n - k] = -inIm[k];
    }
    if (!this.outRe) {
      this.outRe = new Float64Array(n);
      this.outIm = new Float64Array(n);
    }
    this.transform(re, im, this.outRe, this.outIm, true);
    for (let i = 0; i < n; i++) output[i] = this.outRe[i];
  }
}
