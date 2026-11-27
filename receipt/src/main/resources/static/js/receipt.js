// レシート読み取り画面の処理
(function () {
  "use strict";

  var scanUrlMeta = document.querySelector('meta[name="scan-url"]');
  if (!scanUrlMeta) {
    return;
  }
  var scanUrl = scanUrlMeta.content;
  var csrfTokenMeta = document.querySelector('meta[name="csrf-token"]');
  var csrfHeaderMeta = document.querySelector('meta[name="csrf-header"]');
  var csrfToken = csrfTokenMeta ? csrfTokenMeta.content : "";
  var csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.content : "";

  var fileInput = document.getElementById("file");
  var cameraInput = document.getElementById("camera-file");
  var cameraBtn = document.getElementById("camera-btn");
  var galleryBtn = document.getElementById("gallery-btn");
  var form = document.getElementById("scan-form");
  var dropzone = document.getElementById("dropzone");
  var preview = document.getElementById("preview");
  var scanBtn = document.getElementById("scan-btn");
  var loading = document.getElementById("loading");
  var loadingText = document.getElementById("loading-text");
  var errorBox = document.getElementById("scan-error");
  var errorText = document.getElementById("scan-error-text");
  var errorDetail = document.getElementById("scan-error-detail");
  var resultCard = document.getElementById("result");
  var resultAmount = document.getElementById("result-amount");
  var resultSub = document.getElementById("result-sub");
  var rescanBtn = document.getElementById("rescan-btn");

  var MAX_BYTES = 20 * 1024 * 1024;
  var MAX_IMAGE_SIDE = 2400;
  var yen = new Intl.NumberFormat("ja-JP");
  var selectedFile = null;

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

  function isImageFile(file) {
    if (!file) {
      return false;
    }
    if (file.type && file.type.indexOf("image/") === 0) {
      return true;
    }
    return /\.(jpe?g|png|heic|heif|webp)$/i.test(file.name || "");
  }

  function showFile(file) {
    if (!file) {
      return;
    }
    if (!isImageFile(file)) {
      selectedFile = null;
      scanBtn.disabled = true;
      showError("画像ファイル（JPEG / PNG など）を選択してください。");
      return;
    }
    if (file.size > MAX_BYTES) {
      selectedFile = null;
      scanBtn.disabled = true;
      showError("画像サイズが大きすぎます。20MB以下の画像を選択してください。");
      return;
    }

    clearError();
    selectedFile = file;
    if (preview.src) {
      URL.revokeObjectURL(preview.src);
    }
    preview.src = URL.createObjectURL(file);
    preview.hidden = false;
    scanBtn.disabled = false;
  }

  function clearSelectedFile() {
    selectedFile = null;
    fileInput.value = "";
    cameraInput.value = "";
    if (preview.src) {
      URL.revokeObjectURL(preview.src);
    }
    preview.removeAttribute("src");
    preview.hidden = true;
    scanBtn.disabled = true;
  }

  function loadImage(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        URL.revokeObjectURL(url);
        resolve(img);
      };
      img.onerror = function () {
        URL.revokeObjectURL(url);
        reject(new Error("画像を読み込めませんでした。"));
      };
      img.src = url;
    });
  }

  function canvasToBlob(canvas) {
    return new Promise(function (resolve) {
      if (!canvas.toBlob) {
        resolve(null);
        return;
      }
      canvas.toBlob(resolve, "image/jpeg", 0.85);
    });
  }

  // スマートフォンの写真は数MBになることがあるため、送信前に縮小して通信を安定させます。
  async function prepareImage(file) {
    if (file.type === "image/jpeg" && file.size <= 1.5 * 1024 * 1024) {
      return { data: file, name: file.name || "receipt.jpg" };
    }

    try {
      var img = await loadImage(file);
      var scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(img.width, img.height));
      var width = Math.max(1, Math.round(img.width * scale));
      var height = Math.max(1, Math.round(img.height * scale));

      var canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;

      var ctx = canvas.getContext("2d");
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, width, height);
      ctx.drawImage(img, 0, 0, width, height);

      var blob = await canvasToBlob(canvas);
      if (blob && blob.size < file.size) {
        var name = (file.name || "receipt").replace(/\.[^.]+$/, "") + ".jpg";
        return { data: blob, name: name };
      }
    } catch (err) {
      // 変換できない形式は元のまま送信し、サーバー側のエラー表示に任せます。
    }

    return { data: file, name: file.name || "receipt.jpg" };
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
      showError("合計金額を読み取れませんでした。明るい場所で撮り直すか、手入力で登録してください。");
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

  cameraInput.addEventListener("change", function () {
    showFile(cameraInput.files[0]);
  });

  cameraBtn.addEventListener("click", function () {
    cameraInput.value = "";
    cameraInput.click();
  });

  galleryBtn.addEventListener("click", function () {
    fileInput.value = "";
    fileInput.click();
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
      showFile(files[0]);
    }
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();

    if (!selectedFile) {
      showError("レシート画像を選択してください。");
      return;
    }

    clearError();
    resultCard.hidden = true;
    scanBtn.disabled = true;
    loading.hidden = false;
    loadingText.textContent = "画像を準備しています…";

    var controller = new AbortController();
    var timer = setTimeout(function () {
      controller.abort();
    }, 120000);

    try {
      var prepared = await prepareImage(selectedFile);

      loadingText.textContent = "レシートを読み取っています…";

      var body = new FormData();
      body.append("file", prepared.data, prepared.name);

      // ポート転送環境ではカスタムヘッダーが落ちることがあるため、CSRFトークンはフォーム項目にも入れます。
      if (csrfToken) {
        body.append("_csrf", csrfToken);
      }

      var options = {
        method: "POST",
        body: body,
        headers: {},
        credentials: "same-origin",
        cache: "no-store",
        signal: controller.signal
      };
      if (csrfToken && csrfHeader) {
        options.headers[csrfHeader] = csrfToken;
      }

      var response = await fetch(scanUrl, options);
      var contentType = response.headers.get("content-type") || "";

      if (contentType.indexOf("application/json") === -1) {
        if (response.redirected || (response.url || "").indexOf("/login") >= 0) {
          showError("ログインの有効期限が切れました。ログインし直してください。");
        } else {
          showError("サーバーから予期しない応答が返りました。ログインし直して、もう一度お試しください。");
        }
        return;
      }

      var data = await response.json().catch(function () {
        return {};
      });

      if (!response.ok) {
        var message = data.error;
        if (!message) {
          if (response.status === 401 || response.status === 403) {
            message = "ログインの有効期限が切れました。ログインし直してください。";
          } else if (response.status === 413) {
            message = "画像サイズが大きすぎます。別の画像を選ぶか、撮り直してください。";
          } else {
            message = "読み取りに失敗しました（HTTP " + response.status + "）。";
          }
        }
        showError(message, data.detail);
        return;
      }

      renderResult(data);
    } catch (err) {
      if (err && err.name === "AbortError") {
        showError("読み取りに時間がかかりすぎました。通信状況を確認して、もう一度お試しください。");
      } else {
        showError("サーバーに接続できませんでした。通信状況を確認し、画像を選び直してから再試行してください。");
      }
    } finally {
      clearTimeout(timer);
      loading.hidden = true;
      loadingText.textContent = "レシートを読み取っています…";
      scanBtn.disabled = !selectedFile;
    }
  });

  rescanBtn.addEventListener("click", function () {
    clearSelectedFile();
    resultCard.hidden = true;
    clearError();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });
})();
