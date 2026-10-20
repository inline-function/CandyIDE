# 注解处理器

注解处理器是编译期的**用户扩展点**：它是一段**用 Sublimation 自己写的程序**，由解释器
解释执行；它读注解、输出两类编辑——**注解编辑**（加/删注解）与**结构性编辑**（改函数体、插语句、
增删声明，即真正的 AST→AST 变换）——编译器在语义检查前统一应用，让注解真正改变代码的语义。

## 为什么要解释执行

处理器跑在解释器上：它能调用语言本身的 `when`/递归/`List`，与被处理的代码同源，
不需要为处理器单独设计 DSL（Babel 插件式的 AST→AST 管道，但处理器作者写的是普通 Sublimation 脚本）。
编译器内置 `ProcessorEngine`：多轮驱动、幂等收敛、轮数上限保护。

## 管线位置

```
解析 -> 符号收集(阶段A) -> [注解处理器] -> 语义检查(阶段B/C) -> 代码生成
```

处理器**先于**语义检查跑：处理器的产出（如 `@allMut` 引入的 `@mut`）是“要被合法化的代码”，
变换后再统一进检查器——“所有代码都过检查”的契约仍然成立，处理器不能绕过语言设计。

## 内建注解：@allMut

标在结构体上，表示**结构体可变、全部字段也可变**。处理器把它等价改写为：

```subl
@allMut
struct Point(x: Nat, y: Nat)

// 处理器变换为：
@mut struct Point(@mut x: Nat, @mut y: Nat)
```

变换之后，下面的代码才合法（没有 `@allMut` 时同一个赋值被 `E-NOT-MUT` 拒绝——实测对比）：

```subl
@allMut
struct Point(x: Nat, y: Nat)

@impure
fun main() {
    var p: @mut Point = Point(1, 2)
    p.x = 5
    print(toStr(p.x))          // 5
}
```

## 处理器文件

处理器脚本以 `.subs` 文件存在：

- **`annotation processer/`**（相对**工作目录**，编译器从哪里启动就从哪里找）——内建 `@allMut`
  脚本就在这里：`annotation processer/allMut.subs`；目录不存在时**静默跳过**（适合打包发布）；
- **文件主名 = 处理器名 = 关注的注解名**（`allMut.subs` → 处理器 `allMut`，只对 `@allMut` 起反应；
  引擎据此跳过无关 AST，零开销）；
- 文件里是**普通脚本**：顶层语句直接执行，用 `//` 写注释（`#` 不是注释，会报语法错），
  遍历列表用 `when(Nil/Cons)`，lambda 与 when 臂的 `=>` 与普通程序相同。

因为默认目录相对工作目录，随身携带的工程（如 `show/编译器/`）往往没有它——这正是 `-p` 的用途。

### 编译时附加处理器：`-p <路径>`

```bat
java -jar sublimation.jar compile demo/app -p "annotation processer"   :: 装载默认目录
java -jar sublimation.jar compile demo/app -p myprocs/mine.subs         :: 单文件
java -jar sublimation.jar check demo/app -p procsA -p procsB -v         :: 可多次、可与 -v 并用
```

- 目录参数收入其**直接**下的全部 `.subs`（不递归子目录；按文件名排序，注册序稳定）；
- 路径不存在 → `[错误] 注解处理器路径不存在: <路径>`；
- 存在但不是 `.subs` 文件或目录 → `E-PROCESSOR-LOAD`（装载失败，编译立即终止）；
- 无论是否传 `-p`，默认目录的处理器总会装载；`check` / `compile` 在**单文件与目录模式**下都跑处理器。

## 宿主 API

处理器脚本通过一组**宿主函数**只读查询声明、累积注解编辑、报告诊断（脚本里直接调用，
不需要声明）：

| 宿主函数 | 作用 |
|---|---|
| `structNames()` | 全部结构体名（声明序） |
| `structsWithAnnotation(a)` | 带注解 `a` 的结构体名 |
| `structAnnotations(n)` / `structHasAnnotation(n, a)` | 结构体的注解列表 / 是否有注解 `a` |
| `structFields(n)` / `fieldAnnotations(s, f)` | 字段名（声明序）/ 字段的注解列表 |
| `funNames()` / `funsWithAnnotation(a)` / `funAnnotations(n)` | 函数族查询 |
| `addStructAnnotation(n, a)` / `removeStructAnnotation(n, a)` | 结构体注解增删 |
| `addFieldAnnotation(s, f, a)` / `removeFieldAnnotation(s, f, a)` | 字段注解增删 |
| `addFunAnnotation(n, a)` / `removeFunAnnotation(n, a)` | 函数注解增删 |
| `processorError(m)` / `processorWarn(m)` / `processorHint(m)` / `processorInfo(m)` | 报四级诊断 |

> **坑**：0 参宿主函数（`structNames`、`funNames`）在脚本里被注入了一个占位形参（`x: Any`），
> 调用时得传一个任意实参，例如 `structNames(0)`——直接写 `structNames()` 会报 `E-ARITY`。

脚本执行期**不改 AST**：编辑函数只累积指令，引擎收尾时以不可变方式重建 AST
（AST 节点是 data class，只能创建新实例）。增删注解幂等：注解已存在不重复加，
删不存在的注解也不报错——配合引擎多轮自然收敛。结构性编辑（见下节）同样走
「累积指令 → 收尾解析片段并嫁接 → 重查」的管道。

## 结构性编辑：修改 AST

注解编辑之外，处理器还能**修改 AST 本体**（root doc《注解处理器.md》的核心设计：
处理器是 AST→AST 变换，区别于 Java APT 只能生成新文件、Rust proc-macro 只能生成 token）。
编辑以**源码片段**的形式发出——脚本累积指令，引擎收尾时把片段解析成 AST 节点再
不可变地嫁接回去，产物仍整体过重查。

| 宿主函数 | 作用 |
|---|---|
| `setFunBody(name, src)` | 把函数 name 的体替换为 src 解析出的块体「{ … }」或表达式 |
| `prependStmts(name, stmts)` | 把语句片段插到函数块体最前；表达式体自动脱糖成 return 块 |
| `addDecl(src)` | 追加一条新声明（fun/struct/enum/class/impl/type） |
| `replaceDecl(name, src)` | 整体替换名为 name 的声明 |

诊断与安全（实测）：

- 片段解析失败 → **E-PROCESSOR-EDIT**（错误级；该条编辑被丢弃，同批其他编辑照常生效）；
- 目标声明不存在 → **W-PROCESSOR-EDIT**（警告级）；
- **产物整体过重查**：替换出的体若是非法代码（如类型不匹配），E-TYPE-MISMATCH 等正常
  编译错误会拦截整次编译——处理器不能绕过语言设计（root doc 核心契约）；
- 引擎多轮收敛（上限 5 轮，超限 E-PROCESSOR-LOOP）。

### 幂等与收敛

结构性编辑必须幂等，否则引擎判定「每轮都有变化」最终 E-PROCESSOR-LOOP：

- `setFunBody` / `replaceDecl`：每轮发同样的片段 → 产物深度相等 → 停机；
- `addDecl`：同名声明已存在则跳过（引擎内建幂等）；
- `prependStmts`：每轮都会真正改变语句序列——**必须自己守**，最方便的是配注解标记。

```subs
// trace.subs —— 给标了 @trace 的函数体最前插入一行跟踪打印（注解标记守，幂等）
fun containsFun(f: Str): Bool = contains(funAnnotations(f), "traced")

fun run(xs: List[Str]): Null = when(xs) {
    Nil => null
    is Cons(f, rest) => {
        if (containsFun(f)) { null } else {
            prependStmts(f, listOf("print(toStr(111))"))
            addFunAnnotation(f, "traced")
        }
        run(rest)
    }
}
run(funsWithAnnotation("trace"))
```

### 示例：生成新声明 + 替换声明

`gen.subs`（实测：magic 的体被换成 triple(2)，另生成 triple；输出 6 与 12，多轮收敛）：

```subs
// gen.subs
addDecl("fun triple(x: Nat): Nat = x * 3")

fun loop(xs: List[Str]): Null = when(xs) {
    Nil => null
    is Cons(f, rest) => {
        replaceDecl(f, "fun " + f + "(): Nat = triple(2)")
        loop(rest)
    }
}
loop(funsWithAnnotation("gen"))
```

被处理的工程（命令：`compile myapp -p gen.subs -o out.js` 后 node 运行）：

```subl
@gen
fun magic(): Nat = 0

@impure
fun main() {
    print(toStr(magic()))      // 6（体被替换为 triple(2)）
    print(toStr(triple(4)))    // 12（处理器生成的声明）
}
```

> 片段里的字符串按 Sublimation 普通字符串写（含转义引号）；语句列表用 `listOf(...)`。

## 示例：自定义处理器

`twice.subs`——给标了 `@twice` 的函数逐个报警告（实测通过）：

```subs
// twice —— 标了 @twice 的函数名收集后报 processorWarn
fun warnAll(xs: List[Str]): Null = when(xs) {
    Nil => null
    is Cons(s2, rest) => {
        processorWarn(concat("twice hits ", s2))
        warnAll(rest)
    }
}
warnAll(funsWithAnnotation("twice"))
```

被处理的工程与命令（`demo/cproc/` 的真实运行结果）：

```subl
// demo/cproc/main.subl
@twice
fun helper(): Nat = 1

@impure
fun main(): Null {
    print(toStr(helper()))
}
```

```bat
java -jar sublimation.jar check demo/cproc -p demo/cproc
# [警告] W-PROCESSOR-SCRIPT: twice hits helper
# [完成] 模块检查通过
```

## 执行模型与收敛

引擎按注册序逐处理器跑，一轮结束后若 AST 变化则进入下一轮：

- **最大 5 轮**；某轮所有处理器都没改变 AST 即收敛退出；
- 超过 5 轮仍有变化 → `E-PROCESSOR-LOOP: 注解处理器在 5 轮内未收敛（可能产生触发自己的代码）`。
  典型写法是“翻转”型脚本：有注解 `flip` 的结构体删掉、没有的加上——每轮都变，永不收敛；
- 脚本抛异常（类型检查失败/运行时错误） → `E-PROCESSOR-ERROR`（附处理器名与原因），AST 不变；
- 脚本调 `processorError(m)` → `E-PROCESSOR-SCRIPT: <m>`；`processorWarn/Hint/Info`
  分别对应 `W/H/D-PROCESSOR-SCRIPT`（补充级只在 `-v` 下显示）；
- 编辑目标不存在（名字拼错） → `W-PROCESSOR-EDIT: 处理器编辑未命中：声明 X 的注解 a（目标不存在？）`，
  其余编辑继续生效。

## 诊断码

| 码 | 含义 |
|---|---|
| `E-PROCESSOR-SCRIPT` | 处理器脚本主动调 `processorError` 报错 |
| `E-PROCESSOR-ERROR` | 处理器脚本执行失败（脚本类型检查/运行时错误） |
| `E-PROCESSOR-LOOP` | 处理器循环不收敛（反复改写超过 5 轮） |
| `W-PROCESSOR-SCRIPT` | 脚本警告（`processorWarn`） |
| `H-PROCESSOR-SCRIPT` / `D-PROCESSOR-SCRIPT` | 脚本提示 / 补充（`processorHint`/`processorInfo`） |
| `W-PROCESSOR-EDIT` | 编辑的注解目标不存在（名字拼错？） |

> `explain` 收录了 SCRIPT/ERROR/LOOP 三个处理器错误码；装载失败用的 `E-PROCESSOR-LOAD`
> 由 CLI 直接输出，不在 `explain` 表中。

## 宿主外壳（用脚本驱动整套流程）

`AnnotationProcessorHost` 把处理器包成可从脚本驱动的宿主：脚本模式里用 `@host` 声明
`loadProject(path: Str)` / `processAnnotations()` / `writeGenerated(path: Str)`（另有
`annotatedCount()` / `diagnosticReport()` / `hasErrors()`），即可加载工程、跑处理器、写出产物：

```subs
@host fun loadProject(path: Str): Null
@host fun processAnnotations(): Null
@host fun writeGenerated(path: Str): Null

loadProject("myproject/")
processAnnotations()
writeGenerated("out.subl")
```

## 限制

- 只能加/删**注解**，不能新增声明、改类型或改表达式（编辑只作用于注解列表）；
- 查询只覆盖**结构体与函数**（字段注解可查）；enum/class/impl 不在脚本 API 里；
- `typeOf` 类型查询固定返回 null（类型信息在检查器的弥散上下文里，不在 AST 节点上）；
- settings 里注册第三方处理器（`processor` 行）仍未支持，扩展点目前是 `-p` 路径。

完整的可运行示例见演示目录 `show/编译器/`（`compile . -p apt` → node），
其中 `apt/allMut.subs` 与 `apt/twice.subs` 就是本文的两个例子。