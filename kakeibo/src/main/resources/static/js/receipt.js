// レシート読み取り画面の処理
(function () {
  "use strict";

  var scanUrlMeta = document.querySelector('meta[name="scan-url"]');
  if (!scanUrlMeta) {
    return;
  }
  var scanUrl = scanUrlMeta.content;

  var fileInput = document.getElementById("file");
  var form = document.getElementById("scan-form");
  var dropzone = document.getElementById("dropzone");
  var preview = document.getElementById("preview");
  var scanBtn = document.getElementById("scan-btn");
  var loading = document.getElementById("loading");
  var errorBox = document.getElementById("scan-error");
  var errorText = document.getElementById("scan-error-text");
  var errorDetail = document.getElementById("scan-error-detail");
  var resultCard = document.getElementById("result");
  var resultAmount = document.getElementById("result-amount");
  var resultSub = document.getElementById("result-sub");
  var rescanBtn = document.getElementById("rescan-btn");

  var MAX_BYTES = 10 * 1024 * 1024;
  var yen = new Intl.NumberFormat("ja-JP");

  function clearError() {
    errorBox.hidden = true;
    errorText.textContent = "";
    errorDetail.textContent = "";
    errorDetail.hidden = true;
  }

  function showError(message, detail) {
    errorText.textContent = message;
    if (detail) {
      errorDetail.textContent = detail;
      errorDetail.hidden = false;
    } else {
      errorDetail.hidden = true;
    }
    errorBox.hidden = false;
    errorBox.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function showFile(file) {
    if (!file) {
      return;
    }
    if (!file.type || file.type.indexOf("image/") !== 0) {
      showError("画像ファイル（JPEG / PNG など）を選択してください。");
      return;
    }
    if (file.size > MAX_BYTES) {
      showError("画像サイズが大きすぎます。10MB以下の画像を選択してください。");
      return;
    }

    clearError();
    if (preview.src) {
      URL.revokeObjectURL(preview.src);
    }
    preview.src = URL.createObjectURL(file);
    preview.hidden = false;
    scanBtn.disabled = false;
  }

  function todayString() {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, "0");
    var day = String(d.getDate()).padStart(2, "0");
    return d.getFullYear() + "-" + m + "-" + day;
  }

  function renderResult(data) {
    var amount = Number(data.amount);
    if (isNaN(amount) || amount <= 0) {
      showError("合計金額を読み取れませんでした。明るい場所で撮り直してください。");
      return;
    }

    resultAmount.textContent = "¥" + yen.format(amount);
    document.getElementById("itemName").value = data.itemName || "レシート";
    document.getElementById("amountJpy").value = amount;
    document.getElementById("expenseDate").value = data.date || todayString();

    var categorySelect = document.getElementById("category");
    var category = data.category || "その他";
    var hasOption = Array.prototype.some.call(categorySelect.options, function (option) {
      return option.value === category;
    });
    categorySelect.value = hasOption ? category : "その他";

    var currency = data.currency || "JPY";
    document.getElementById("currency").value = currency;
    document.getElementById("originalAmount").value = amount;

    resultSub.classList.remove("warn");
    if (currency !== "JPY") {
      resultSub.textContent = "外貨（" + currency + "）で読み取りました。円換算額を確認してください。";
      resultSub.classList.add("warn");
    } else {
      resultSub.textContent = "品目・金額・日付は必要に応じて修正してください。";
    }

    resultCard.hidden = false;
    resultCard.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  fileInput.addEventListener("change", function () {
    showFile(fileInput.files[0]);
  });

  dropzone.addEventListener("dragover", function (event) {
    event.preventDefault();
    dropzone.classList.add("is-dragover");
  });

  dropzone.addEventListener("dragleave", function () {
    dropzone.classList.remove("is-dragover");
  });

  dropzone.addEventListener("drop", function (event) {
    event.preventDefault();
    dropzone.classList.remove("is-dragover");
    var files = event.dataTransfer && event.dataTransfer.files;
    if (files && files.length > 0) {
      fileInput.files = files;
      showFile(files[0]);
    }
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    var file = fileInput.files[0];
    if (!file) {
      showError("レシート画像を選択してください。");
      return;
    }

    clearError();
    resultCard.hidden = true;
    scanBtn.disabled = true;
    loading.hidden = false;

    var body = new FormData();
    body.append("file", file);

    try {
      var response = await fetch(scanUrl, { method: "POST", body: body });
      var data = await response.json().catch(function () {
        return {};
      });

      if (!response.ok) {
        showError(
          data.error || "読み取りに失敗しました（HTTP " + response.status + "）。",
          data.detail
        );
        return;
      }

      renderResult(data);
    } catch (err) {
      showError("サーバーに接続できませんでした。ネットワーク接続を確認してください。");
    } finally {
      loading.hidden = true;
      scanBtn.disabled = false;
    }
  });

  rescanBtn.addEventListener("click", function () {
    fileInput.value = "";
    if (preview.src) {
      URL.revokeObjectURL(preview.src);
    }
    preview.removeAttribute("src");
    preview.hidden = true;
    scanBtn.disabled = true;
    resultCard.hidden = true;
    clearError();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });
})();
