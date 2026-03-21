document.getElementById('pingBtn').addEventListener('click', async function () {
    const resultEl = document.getElementById('apiResult');
    resultEl.textContent = '请求中...';
    try {
        const resp = await fetch('/api/users/ping');
        const data = await resp.json();
        resultEl.textContent = JSON.stringify(data, null, 2);
    } catch (e) {
        resultEl.textContent = '请求失败: ' + e.message;
    }
});
