# 语言样式规范

写出让同事（和三个月后的自己）一眼就懂的 Sublimation 代码。

> v4 终版约定：**when 分支与 lambda 一律用 `=>`**。lambda 的 `->` 旧写法解析器仍接受，但文档、示例统一 `=>`，避免与命题蕴含混读。

## 核心原则：多用语法糖

凝华提供了丰富的语法糖，它们不仅更短，**可读性也更好**。不要为了"显式"而拒绝糖。

### 1. 表达式的首选写法

| ✅ 推荐 | ❌ 不推荐 | 说明 |
|---------|-----------|------|
| `xs.map { x => x + 1 }` | 手写递归 | 函数式链式调用（尾随 lambda） |
| `xs.filter { x => x > 0 }` | 手写 `when` 过滤 | 用容器方法 |
| `o.getOrElse(0)` | 手写解构/默认 | Optional 方法 |
| ``"Hello, $name"`` / ``"sum=${x + y}"`` | ``"Hello, " + name`` | 字符串插值（`$name` / `${expr}`） |
| `when (m) { is Some(v) => v; None => 0 }` | if-else 链 | 穷尽性交给编译器 |
| `type NatOpt = Optional[Nat]` | 每处写全名 | 类型别名（决策 30） |

### 2. when 穷尽性

把区间判断放进 `when(n in _)` 让编译器自动证明覆盖（注意全界要写成命题 `≤`）：

```subl
// ✅ 推荐：编译器自动证明区间覆盖 (0..30)
fun grade(n: Nat)<n ≤ 30>: Str =
    when(n in _) {
        (0 .. 10)  => "低"
        (11 .. 20) => "中"
        (21 .. 30) => "高"
    }

// ❌ 不推荐：if-else 链，编译器帮不了你
fun gradeByIf(n: Nat): Str {
    if (n <= 10) { return "低" }
    else if (n <= 20) { return "中" }
    else { return "高" }
}
```

结构体字段同理——伴生命题 `<score ≤ 100>` 建域，`impl` 方法里直接写无主题区间 when：

```subl
struct Student(name: Str, score: Nat)<score ≤ 100>

class Gradable { fun grade(): Grade }
impl Gradable for Student {
    fun grade(): Grade = when {
        score in (90..100) => Grade::A
        score in (80..89)  => Grade::B
        score in (60..79)  => Grade::C
        score in (0..59)   => Grade::D
    }
}
```

### 3. 前置条件放类型里，不作防御检查

```subl
// ✅ 推荐：类型写明 n ≤ 100，调用方自动证明
fun capped(n: Nat)<n ≤ 100>: Nat = n

// ❌ 不推荐：运行时 if 检查
fun cappedByIf(n: Nat): Nat {
    if (n > 100) { return 100 }
    return n
}
```

### 4. Optional 解构用 when 或方法糖

```subl
// ✅ 推荐：when 解构一目了然
when (maybe) {
    is Some(v) => doSomething(v)
    None       => doDefault()
}

// ✅ 也可以：链式方法
var result = maybe.map { v => v + 1 }.getOrElse(0)
```

### 5. 伴生命题：把约束写进结构体本身

```subl
// ✅ 推荐：struct 自己声明约束，构造时证明
struct PosPoint(x: Nat, y: Nat)<x > 0, y > 0>

// ❌ 不推荐：约束靠注释或运行时检查
struct PlainPoint(x: Nat, y: Nat)  // 没有约束
```

## 三种调用法

凝华有三种调用写法，按可读性灵活选用：

### 尾随调用法

函数末尾的 lambda 可以挪到括号外（尾随 lambda）：

```subl
xs.map({ x => x + 1 })              // 常规写法
xs.map { x => x + 1 }               // 尾随调用法（推荐）

xs.fold(0, { a, x => a + x })       // 常规
xs.fold(0) { a, x => a + x }        // 尾随
```

### 中缀调用法

二元函数都能写中缀：`a f b` 等价于 `f(a, b)`。运算符本身就是函数：

```subl
1 + 2            // 即 +(1, 2)
xs append ys     // 即 append(xs, ys)，也可写成 xs.append(ys)
```

### 扩展调用法

自由函数 `f(o, …)` 的第一个参数是对象时，可写成方法糖 `o.f(…)`：

```subl
size(xs)         // 自由函数调用
xs.size()        // 扩展调用法（等价，推荐）

get(m, "a")      // 自由函数
m.get("a")       // 扩展调用法（等价）
```

> `o.f(…参数)` 的解析顺序：先找自由函数/扩展方法 `f(o, …参数)`；没有再看类型类字典里的 `f`。容器方法（`map`/`fold`/`getOrElse`…）都是这条糖。

## 类型别名（决策 30）

名字长了、泛型套娃了就用 `type` 起短名，使用点自动展开：

```subl
type NatOpt = Optional[Nat]           // 具名展开
type Box[a] = Optional[a]             // 泛型头：Box[Nat] ≡ Optional[Nat]
type Dict[k, v] = Map[k, v]           // 多参数：Dict[Str, Nat] ≡ Map[Str, Nat]

fun unwrap(o: NatOpt): Nat = when(o) {
    is Some(v) => v
    None       => 0
}
```

别名是纯类型层：不生成代码、参与 `check` 校验。右值支持具名/应用类型、函数类型与元组类型（`type Pred = (Nat) => Bool`、`type PairT = (Nat, Str)` 都合法）。

## 命名规范

| 项目 | 规范 | 示例 |
|------|------|------|
| 函数名 | 小驼峰 | `getUser` `calcScore` |
| 变量名 | 小驼峰 | `userName` `totalCount` |
| 类型参数 | 大写字母 | `List[T]` `Map[K, V]` |
| 命题参数 | @小写 | `@p` `@q` |
| 结构体/类型类 | 大驼峰 | `User` `Functor` |
| 类型别名 | 大驼峰 | `NatOpt` `Dict` |
| impl 块 | 类型无特殊命名 | `impl Show for User` |
| 定理函数 | 描述性名称 | `rangeLowerBound` `leqTrans` |

## 模块组织

```
MY_PROJECT/
  main.subl                ← 入口（必须有 fun main()）
  core/                    ← 标准库包（复制一份即隐式挂载，裸名直接用）
  users/users.subl         ← 子模块目录
  users/users.settings     ← 模块声明（mount 依赖，见《模块系统》）
```

## 注释与文档

```subl
// 这是单行注释——解释为什么这样做，而不是做了什么

// --- 块注释风格：用 --- 分隔逻辑段落 ---

// TODO: 这是一条待办（编译器不解析，给人看的）
```

## 检查清单

在你提交代码前，确认：

- [ ] when 分支与 lambda 都用 `=>`（`->` 只留在旧代码里）
- [ ] 表达式链够短（单行完成的事不分三行）
- [ ] 前置条件用类型（伴生命题/函数上文 `<…>`），不靠 if 防御
- [ ] when 穷尽性交给编译器（区间用 `when(n in _)` 或无主题区间 when，全界写进命题 `≤`）
- [ ] Optional 不裸 if/else——用 when 或链式方法
- [ ] 长类型用别名缩短
