# Git 历史大文件清理指南

> 用于解决 push 缓慢问题：从 Git 历史中移除 `product-service/target/`（含 35MB JAR）等大文件。

---

## 一、清理前须知

- **会改写历史**：清理后所有 commit 的 hash 会改变，已 push 的仓库需要 `git push --force`
- **协作注意**：若其他人已拉取该仓库，需协调后统一重置本地分支
- **建议**：先备份整个项目文件夹，或确保有远程备份

---

## 二、安装 git-filter-repo

Windows 下用 pip 安装：

```powershell
pip install git-filter-repo
```

若无 Python，可安装 [Python](https://www.python.org/downloads/) 或改用下方的 **BFG 方案**。

---

## 三、执行清理

在项目根目录 `E:\desktop\distributedSW` 下执行：

### 1. 移除 product-service/target/（约 35MB，主要收益）

```powershell
git filter-repo --path product-service/target --invert-paths
```

### 2. 可选：移除未引用的 img/image.png（约 1MB）

README 中未引用 `image.png`，若确认不需要可一并删除：

```powershell
git filter-repo --path img/image.png --invert-paths
```

### 3. 一次性移除多个路径

```powershell
git filter-repo --path product-service/target --invert-paths --path img/image.png --invert-paths
```

---

## 四、重新添加远程并强制推送

`git filter-repo` 会移除原有 remote 配置，需要重新添加并强制推送：

```powershell
git remote add origin <你的远程仓库地址>
git push --force origin main
```

若远程分支名是 `master`，将 `main` 改为 `master`。

---

## 五、验证效果

清理后查看仓库大小：

```powershell
git count-objects -vH
```

或再次列出大对象（应不再包含 target 和 image.png）：

```powershell
git rev-list --objects --all | git cat-file --batch-check="%(objecttype) %(objectname) %(objectsize) %(rest)" | Sort-Object { [int]($_ -split "\s+")[2] } -Descending
```

---

## 六、备选：使用 BFG Repo-Cleaner

若无法使用 `git filter-repo`，可用 [BFG](https://rtyley.github.io/bfg-repo-cleaner/)（Java 程序，Windows 可直接运行）：

1. 下载 `bfg.jar`
2. 在项目根目录执行：

```powershell
# 删除 target 文件夹的所有历史（含 product-service/target、user-service/target 等）
java -jar bfg.jar --delete-folders target

# 清理并压缩
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# 强制推送（BFG 不会移除 remote，直接推送即可）
git push --force origin main
```

---

## 七、.gitignore 检查

根目录 `.gitignore` 已包含：

```
target/
*.jar
*.class
```

可确保之后不会再次误提交 JAR 和编译产物。`product-service/` 下的构建产物会被 `target/` 规则覆盖。

---

## 八、后续建议

- **图片**：README 引用的 `img/image-2.png` ~ `image-6.png` 可考虑用 [TinyPNG](https://tinypng.com/) 等工具压缩，减小仓库体积
- **课程作业**：若远程仓库为课程提交用，`git push --force` 前请确认是否允许强制推送
