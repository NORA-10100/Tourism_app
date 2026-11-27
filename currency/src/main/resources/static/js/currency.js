(function () {
  'use strict';

  var app = document.getElementById('fx-app');
  if (!app) return;

  var form = document.getElementById('fx-form');
  var amount = document.getElementById('fx-amount');
  var from = document.getElementById('fx-from');
  var to = document.getElementById('fx-to');
  var swap = document.getElementById('fx-swap');
  var refresh = document.getElementById('fx-refresh');
  var inputError = document.getElementById('fx-input-error');
  var resultMain = document.getElementById('fx-result-main');
  var resultSub = document.getElementById('fx-result-sub');
  var rateRows = Array.prototype.slice.call(
    document.querySelectorAll('#fx-rate-list [data-code]')
  );
  var rates = Object.create(null);
  var locale = (document.documentElement.lang || 'ja-JP').replace('_', '-');

  rates.JPY = 1;

  rateRows.forEach(function (row) {
    var code = row.dataset.code;
    var yenPerUnit = Number(row.dataset.rateToJpy);
    if (!code || !Number.isFinite(yenPerUnit) || yenPerUnit <= 0) return;

    rates[code] = 1 / yenPerUnit;
    var tenThousand = 10000 / yenPerUnit;
    row.querySelector('.fx-rate-ten-thousand').textContent =
      app.dataset.tenThousandTemplate
        .replace('{0}', formatMoney(tenThousand, code))
        .replace('{1}', code);
  });

  swap.disabled = false;

  function parseAmount(raw) {
    var normalized = String(raw == null ? '' : raw)
      .normalize('NFKC')
      .replace(/[\s,]/g, '')
      .replace(/^[−‐-―ー]/, '-');
    if (normalized === '') return { error: app.dataset.errorEmpty };
    if (normalized.charAt(0) === '-') return { error: app.dataset.errorNegative };

    var valueText = normalized.charAt(0) === '+' ? normalized.slice(1) : normalized;
    if (!/^(?:\d+(?:\.\d*)?|\.\d+)$/.test(valueText)) {
      return { error: app.dataset.errorInvalid };
    }

    var value = Number(valueText);
    if (!Number.isFinite(value)) return { error: app.dataset.errorInvalid };
    if (value > 1e12) return { error: app.dataset.errorTooLarge };
    return { value: value };
  }

  function fractionDigits(code) {
    try {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: code
      }).resolvedOptions().maximumFractionDigits;
    } catch (error) {
      return 2;
    }
  }

  function formatMoney(value, code) {
    var digits = fractionDigits(code);
    return new Intl.NumberFormat(locale, {
      maximumFractionDigits: value > 0 && value < 1 ? Math.max(digits, 4) : digits
    }).format(value);
  }

  function formatRate(value) {
    return new Intl.NumberFormat(locale, { maximumSignificantDigits: 5 }).format(value);
  }

  function formatPlain(value) {
    return new Intl.NumberFormat(locale, { maximumFractionDigits: 6 }).format(value);
  }

  function showError(message) {
    inputError.textContent = message || '';
    inputError.hidden = !message;
    amount.setAttribute('aria-invalid', message ? 'true' : 'false');
  }

  function renderResult() {
    var parsed = parseAmount(amount.value);
    resultSub.textContent = '';
    if (parsed.error) {
      showError(parsed.error);
      resultMain.textContent = '—';
      return;
    }
    showError('');

    var fromCode = from.value;
    var toCode = to.value;
    if (!rates[fromCode] || !rates[toCode]) {
      resultMain.textContent = '—';
      resultSub.textContent = app.dataset.errorUnavailable;
      return;
    }

    var converted = parsed.value / rates[fromCode] * rates[toCode];
    resultMain.textContent = formatPlain(parsed.value) + ' ' + fromCode
      + ' = ' + formatMoney(converted, toCode) + ' ' + toCode;

    var forward = rates[toCode] / rates[fromCode];
    var inverse = rates[fromCode] / rates[toCode];
    resultSub.textContent = '1 ' + fromCode + ' = ' + formatRate(forward) + ' ' + toCode
      + ' / 1 ' + toCode + ' = ' + formatRate(inverse) + ' ' + fromCode;
  }

  amount.addEventListener('input', renderResult);
  from.addEventListener('change', renderResult);
  to.addEventListener('change', renderResult);
  swap.addEventListener('click', function () {
    var previousFrom = from.value;
    from.value = to.value;
    to.value = previousFrom;
    renderResult();
  });
  refresh.addEventListener('click', function () {
    window.location.reload();
  });
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    renderResult();
  });
  document.getElementById('fx-rate-list').addEventListener('click', function (event) {
    var row = event.target.closest('.fx-rate-row');
    if (!row) return;
    from.value = row.dataset.code;
    to.value = 'JPY';
    renderResult();
    document.querySelector('.fx-convert').scrollIntoView({ behavior: 'smooth', block: 'start' });
    amount.focus({ preventScroll: true });
  });

  renderResult();
}());
