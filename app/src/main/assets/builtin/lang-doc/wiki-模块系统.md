# 模块系统

Sublimation 的工程组织单位是**模块**：一个目录 = 一个模块。编译入口（一个目录）是根模块，
目录树即模块树，同目录下所有 `.subl` 文件共享一个符号表。模块之间靠 `.settings` 文件里的
`mount` 声明互相装配；`core` 标准库是一个包，对用户模块**隐式挂载**。

## 目录即模块

```
MY_PROJECT/
  main.subl                  ← 根模块的源文件（fun main() 写在任意模块里都可以）
  core/                      ← core 标准库包（目录下只有子包，隐式自动挂载，见下节）
  users/                     ← 子模块目录
    users.subl               ← 模块源码（同目录所有 .subl 共享符号表）
    users.settings           ← 模块声明（mount 依赖）
  billing/
    billing.subl
```

- 根模块（编译入口目录）**没有 settings 文件**——它的模块名为空，装载时直接跳过（历史限制）；
  这不影响可见性：core 对根模块同样隐式挂载，子目录按限定名可达。
- 扫描时忽略隐藏目录与 `build` `out` `target` `node_modules` `.git` `.gradle` `.idea` `.kotlin`。

## 挂载 mount

在 `<模块名>.settings` 里逐行声明依赖（一行一个指令，`#` 开头是注释）：

```
# users.settings
mount billing                    # 挂兄弟模块
mount core.math                  # 挂标准库的 math 子包（点分完整包名，等价于 core/math）
mount core.collection as coll    # 挂到本地别名 coll
```

- `mount <路径> [as <别名>]` 是 settings 支持的**唯一**指令；别的写法报 `E-PARSE-SETTINGS`。
- 路径写法两等价：斜杠形 `core/math` 与点分形 `core.math`（目录名按约定不含点）。
- 挂载是“在当前模块的命名空间里加一条指针”，不复制代码；挂载链成环报 `E-CIRCULAR-MOUNT`。
- 挂载目标不存在报 `E-UNBOUND-NAME`（消息形如“模块 users 的 settings 挂载了不存在的模块路径 'nothere'”）。
- 挂载目标的本地名若与子目录名或别的挂载名相撞，报 `E-DUP-DECL`——
  所以一个模块没法挂自己的直接子目录（目标名字已被子目录占用）。

## core 统一标准库

标准库是名为 **core** 的**包**（目录下只含子包目录、没有直接的 `.subl` 源文件）：

```
core/
  math/math.subl + math.settings       算术：gcd/lcm/isEven/sq …
  collection/*.subl                    List/Set/Map/Optional/Result 与类型类
  string/string.subl                   concat/length/charAt/parseNat …
  logic/logic.subl                     定理：rangeLowerBound、natNonNeg …
  io  json  time  async  random  net  regex  encoding  real  classic
```

编译器按两种布局识别它（相对编译入口根，按优先级）：

1. 入口目录下有 `core/` 子目录（最常见的自包含写法：把仓库根 `core/` 复制一份进工程）；
2. 根下没有 `core/` 而存在 `test/core/` 时，后者被**嫁接**为根的 `core` 子树（路径前缀重算为 `core/…`）。

> 判定“是不是标准库包”看形态：目录里只有子包、没有直接 `.subl`。用户自己建一个 `core/`
> 并往里直接放源文件，它就是普通模块，不会被当作标准库、也不触发隐式挂载。

### 隐式挂载

编译器为每个非 core 的模块注入标准库挂载：core 根及其全部子包按名字挂入
（等价于自动写了 `mount core` + `mount core.math` + `mount core.collection` …）。
用户模块**无需任何 settings** 即可：

- **裸名**调用标准库符号：`gcd(12, 8)`、`Some(3)`、`size(xs)`；
- **限定名**调用：`core.math.gcd(12, 8)`、`core.collection.size(xs)`。

用户模块自带的同名子目录优先：`proj/core/` 存在时，`proj` 的 `core.xxx` 指自己的子模块，
编译器跳过对标准库子包的注入（本地名 core 已被占用，其余子包照常注入）。

## 三种调用形式与可见性

以挂载了 `mount core.math` 的模块为例，三种写法都对（多段挂载会把前缀包一并挂入，
所以 `math` 这个短名也可用）：

```subl
core.math.gcd(12, 8)    // 全限定名
math.gcd(12, 8)         // 短名（挂载目标的前缀包）
gcd(12, 8)              // 裸名（默认导入：core 整包 + 挂载传递）
```

可见性的规则（引用路径一律**相对当前模块视角**）：

- 父→子可见：当前模块可以用限定名访问自己的子目录（`util.dbl(21)`）；
- 挂载向下流淌：祖先的挂载对后代可见，挂载目标的符号经传递可达；
- 子→父不可见、兄弟之间默认不可见：要用就得挂载；
- **裸名**只覆盖“本模块 + core 默认导入 + 自身及祖先挂载的传递闭包”，
  子模块的函数在父模块里**裸调不到**——必须写限定名或挂它。

下面这个通过编译的完整例子（`check` + `compile` + `node` 全部实测通过）覆盖了三种形式：

```
mp/
  core/                                        ← core 标准库副本
  main.subl
  util/util.subl     + util.settings           mount core.math
  alias/alias.subl   + alias.settings          mount core.math as m
```

```subl
// main.subl（根模块）
@impure
fun main() {
    print(toStr(core.math.gcd(12, 8)))   // 全限定：根模块不经挂载即可达
    print(toStr(math.gcd(12, 8)))         // 短名：core 子包隐式挂入
    print(toStr(gcd(12, 8)))              // 裸名：默认导入
    print(toStr(util.dbl(21)))            // 子模块限定调用
    print(toStr(alias.probe()))           // 子模块限定调用
}
```

```subl
// util/util.subl —— 挂载声明在 util.settings 里（mount core.math）
fun dbl(n: Nat): Nat = n + n

// alias/alias.subl —— alias.settings 里写 mount core.math as m
fun probe(): Nat = m.gcd(6, 4)
```

在根模块里直接写 `dbl(21)`（不加限定也不挂 util）会被拒：
`E-UNBOUND-NAME: 没找到函数 dbl——拼写错了？还是没 mount 它所在的模块？`。

## 跨模块解析

构造子（`Ok`/`Err`、`Some`/`None`）、结构体、枚举、函数、类型类方法都会在可见模块
（自己 + 后代 + 挂载闭包 + core）里依次查找。类型类的 `impl` 也随模块可见性走：
想用别的模块的 `impl Show for Point`，把那个模块挂载过来即可。

证明时，定理搜索同样沿可见路径跨模块展开：

```subl
+∵ n ∈ (0..30)
∴ n ≥ 0            // 命中 core/logic/logic.subl 的 rangeLowerBound 与 natNonNeg
```

## 编译与 JS 名字

```bat
java -jar sublimation.jar check MY_PROJECT                 :: 类型检查整个工程
java -jar sublimation.jar compile MY_PROJECT -o out.js     :: 语义通过后生成 JS
node out.js
```

- `check`/`compile` 传**目录**即走模块系统（目录模式只支持这两个命令；脚本目录用 `run-script`）；
- 编译产物是**单个 JS 文件**，跨模块同名符号靠 JS 名字前缀隔离：
  模块路径的 `/` 换成 `_` 再做合法化（`core/math` 的 gcd → `core_math__gcd`，
  `util` 的 dbl → `util__dbl`，根模块符号不加前缀）；
- 整个工程有且仅有一个零参 `fun main()`：多个模块声明 main 报 `E-DUP-DECL`（消息列出全部位置）。

> 目录编译时，core 标准库自身的 `H-ROOT-BUILTIN`/`W-UNUSED` 等提示和警告会出现在输出里
> （不阻断编译）；补充级诊断只保留根模块的，其余模块的被过滤掉。

## 模块相关诊断码

| 码 | 触发场景 |
|---|---|
| `E-PARSE-SETTINGS` | settings 文件出现 `mount` 以外的指令或格式错 |
| `E-CIRCULAR-MOUNT` | 挂载链成环（`a -> b -> a`），只报第一个环 |
| `E-UNBOUND-NAME` | 挂载目标不存在；裸名/限定名在可见模块里找不到 |
| `E-DUP-DECL` | 挂载的本地名与子目录/其他挂载冲突；多个模块声明 `main` |