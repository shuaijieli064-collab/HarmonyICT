# NEXUS · 智能体协助 Git 拉取与推送说明

> 目标：三人各自让 CodeArts 智能体完成 clone / pull / commit / push / PR，通过 GitHub 整合。
> 关键前提：**本地先配好免交互认证**。否则智能体执行 git 命令时会卡在凭证输入，无法自动完成。

---

## 0. 一次性准备

### 0.1 由一人先建立远程仓库（只需做一次）

当前所有文档还在本地工作区，必须先推上 GitHub，其他人才有东西可拉取：

```
# 在有文档的本地目录执行
git init
git add docs/
git commit -m "[chore] 初始化项目文档"
git branch -M main
git remote add origin <你的GitHub仓库地址>
git push -u origin main
```

推送后，把仓库地址发给三位成员。

### 0.2 每人各做一次：配置 Git 身份与认证

```
git config --global user.name "你的名字"
git config --global user.email "你的GitHub邮箱"
```

**认证方式推荐 SSH key（强烈建议，避免智能体卡在凭证输入）：**

```
ssh-keygen -t ed25519 -C "你的GitHub邮箱"     # 一路回车
# 将 ~/.ssh/id_ed25519.pub 的内容添加到 GitHub → Settings → SSH and GPG keys
ssh -T git@github.com                          # 出现 Hi <用户名>! 即成功
```

> 若坚持用 HTTPS + 个人访问令牌（PAT），须先在本地配置凭证存储（`git config --global credential.helper store` 并首次手动登录一次），否则智能体每次 push 都会卡住。**比赛场景推荐直接用 SSH。**

### 0.3 每人各做一次：克隆仓库并建自己的分支

```
git clone <你的GitHub仓库地址>
cd NEXUS
git checkout -b feature/backend-a    # A 用这个
# git checkout -b feature/ai-b        # B 用这个
# git checkout -b feature/frontend-c  # C 用这个
git push -u origin feature/backend-a  # 换成自己分支
```

**之后用 CodeArts 打开这个 NEXUS 文件夹作为工作区，再下发下面的 Prompt。**

---

## 1. 给智能体的 Prompt 模板（按场景复制，方括号内容自行替换）

### 场景一：每日开工同步（每天开始工作前）

```
请在本仓库（工作区根目录即仓库根）执行以下 Git 操作，并把结果汇报给我：
1. 确认当前分支是 [feature/backend-a]；若不是，切换到它。
2. 执行 git fetch origin，然后 git rebase origin/develop，把 develop 上他人的最新提交同步到我的分支。
3. 若 rebase 出现冲突，立即停止，不要自动解决，把冲突文件列表和修改内容汇报给我，等我指令。
4. 执行 git status 汇报当前工作区状态。
注意：不要修改任何代码文件，只做同步；不要 force push。
```

### 场景二：提交并推送（每天收工前）

```
请先按 docs/04-代码规范.md 第 7 节的自检清单检查本次改动，然后执行 Git 提交与推送：
1. 执行 git status 和 git diff --stat，列出本次改动的文件。
2. 只暂存本次任务相关的文件，不要 git add .（避免提交无关文件）。
3. 用规范格式写 commit 消息：[A] 类型: 描述（类型取 feat/fix/docs/refactor/test/chore），按本次改动生成合适的描述，先给我确认。
4. 确认后提交，并 git push origin [feature/backend-a]。
5. 汇报提交哈希与推送结果。
注意：不要提交凭证、密钥、临时文件；不要 push 到 main 或 develop。
```

### 场景三：契约文档定稿后发 PR（A 发 database-design/api-specification，B 发 extraction-schema）

```
我已完成契约文档 [docs/database-design.md]，需要发起 PR 供团队 review：
1. 确认当前在 [feature/backend-a] 分支，且该文档已提交推送。
2. 发起从 [feature/backend-a] 到 develop 的 Pull Request（如可用 gh 命令：gh pr create --base develop --head [feature/backend-a] --title "[A] docs: 数据库设计契约"）。
3. 在 PR 描述里写明：契约用途、需要谁 review（B 或 C）、字段变更点。
4. 若无法直接创建 PR，给出 GitHub 网页端创建 PR 的链接与填写内容。
```

### 场景四：拉取他人契约文档的最新版本

```
我需要获取团队刚合并到 develop 的契约文档更新：
1. git fetch origin。
2. 将 develop 上 [docs/api-specification.md] 的更新合并到我的分支（用 git rebase origin/develop 或 git checkout origin/develop -- 指定文件，优先 rebase）。
3. 用 diff 对比新旧版本，汇报字段变更点，判断是否需要调整我的代码。
4. 若有冲突，停止并汇报，不要自动解决。
```

---

## 2. Git 操作安全红线（写入智能体 Prompt 的固定约束）

1. **禁止 force push**（`git push -f`），除非我明确要求。
2. **禁止直接 push 到 main / develop**，所有改动走 feature 分支 + PR。
3. **禁止提交凭证、密钥、`.env`、token** 等敏感文件；提交前检查 `git status`。
4. **遇到冲突立即停止并汇报**，不要自动解决或丢弃他人代码。
5. **不要 `git add .` 全量暂存**，只暂存本次任务相关文件。
6. **Commit 格式**：`[A/B/C] 类型: 描述`。
7. 任何 destructive 命令（`git reset --hard`、`git clean -fd`、`git checkout --`）执行前必须先向我说明影响并等我确认。

---

## 3. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 智能体执行 push 卡住不动 | HTTPS 认证需要交互输入凭证 | 改用 SSH（见 0.2），或先本地手动登录一次缓存凭证 |
| `rejected` 推送被拒 | 远端分支有他人新提交 | 先执行场景一（fetch + rebase），再 push |
| rebase 冲突 | 两人改了同一文件同一处 | 停止，人工确认保留哪一方，勿让智能体自动合并 |
| `fatal: not a git repository` | 工作区不是仓库根 | 用 CodeArts 打开 NEXUS 文件夹（含 `.git` 的层级）作为工作区 |
| 智能体无终端权限 | IDE 未授权命令执行 | 在 CodeArts 设置里开启终端/命令执行权限，或手动执行后让智能体继续 |

---

## 4. 建议：把红线固化到执行包

三份成员执行包（01/02/03）的 Git 约定段落已指向本说明。三人下发任务时，可在 Prompt 末尾统一加一句：

```
涉及 Git 操作时，严格遵守 docs/05-智能体协助Git拉取与推送说明.md 第 2 节的安全红线。
```
