# 副作用与可变性控制

凝华的纪律：**默认纯、不可变；副作用与可变性必须显式标注**。

> 本文对应根文档《副作用与可变性控制》。所有示例均以 v4 编译器（commit 802b43a，674 项测试全绿）逐条验证。

## 变量：默认不可变

```subl
var n = 0               // 不可变：n 不能再赋值
// n = 1                // ❌ E-IMMUT-ASSIGN

@mut var m = 0
m = m + 1               // ✅ 可变变量需要 @mut 标注
```

> 可变变量除了自反外，不承认它与其他表达式的相等命题——上下文系统里它是"半死不活"的（见《上下文系统》）。

## 纯函数（默认）

```subl
fun add(a: Nat, b: Nat): Nat = a + b       // 纯函数，无需标注
fun pure(x: Nat): Nat { return x + 1 }     // 也是纯
```

纯函数自动获得自然命题 `pure<f>`，可以参与自动证明；若上下文里供给 `pure<f>`，连 `f` 的不纯调用也会被放行：

```subl
@impure fun side(): Null {}
fun pureCaller()<pure<side>>: Null {
    side()                  // 上下文持 pure<side> ⇒ 纯作用域调非纯函数放行
}

@impure
fun main() {
    unchecked.axiom[pure<side>]     // @impure 函数不自动注册 pure，前件需显式供给
    pureCaller()
}
```

纯函数必须满足三件事（编译器检查）：

- 体内不能有不纯表达式（`E-IMPURE-CALL`）
- 不能修改外部捕获的可变变量
- 必须能证明停机（递归收敛）

停机证明有两条路：**结构递归**（每次递归的参数是原参数的子结构，如对子列表递归）或**数值递归**（参数向着某个数值靠拢并在到达时停止）。不满足报 `E-NON-STRUCTURAL-REC`。

## 副作用 @impure

```subl
@impure
fun main() { print("hello") }              // print 是副作用，标 @impure
```

- 参数类型带 `@mut` 的函数必须标 `@impure`（`E-IMPURE-REQUIRED`）——它可能修改传入的可变状态。
- 不纯函数的调用是**不纯表达式**——不承认它的相等命题，包括自反（`print("x") = print("x")` 证不出）。

## 可变结构体

```subl
@mut struct Counter(@mut n: Nat)
```

- `@mut` 标注 struct：允许它声明可变字段；
- 字段再标 `@mut`：该字段可变；
- 修改字段还需要**变量持有的视图可变**：在类型前加 `@mut` 表示可变视图。

```subl
@impure
fun main() {
    var a: @mut Counter = Counter(0)
    a.n = 1                              // ✅ 可变视图 + 可变字段
    var b: Counter = Counter(0)
    // b.n = 1                           // ❌ E-NOT-MUT：不可变视图没有修改权
}
```

## 可变类型（类型类层）

类型类也能标注可变性：

```subl
@mut
class Mutable {
    @mut
    fun bump(): Null
}

impl Mutable for Counter {
    @mut
    fun bump(): Null {
        self.n = self.n + 10
    }
}

@ref var c: @mut Counter = Counter(0)
c.bump()                                 // 可变函数：只能在类型可变时调用
```

为类型类注解 `@mut` 表示允许声明可变函数；可变函数**只能在类型可变的时候调用**，且可以在实现里修改结构体的可变属性。带 `@mut` 形参的方法需要 `@impure`。

## 可变拷贝与深拷贝

可变/不可变类型之间的隐式转换会触发**深拷贝**——拿到的是独立副本，改它不影响源：

```subl
@mut struct Counter(@mut n: Nat)

@impure
fun main() {
    @ref var a: @mut Counter = Counter(0)
    var b: Counter = a                   // @mut → 不可变：深拷贝
    a.n = 5
    print(toStr(b.n))                    // 0

    var plain: Counter = Counter(3)
    @mut var d: @mut Counter = plain     // 不可变 → @mut：深拷贝
    d.n = 99
    print(toStr(plain.n))                // 3

    @mut var e: @mut Counter = a         // @mut → @mut（任一侧非 @ref）：深拷贝
    e.n = 100
    print(toStr(a.n))                    // 5

    @mut var counter = 0
    counter = counter + 1
    print(toStr(counter))                // 1
}
```

规则一句话：**只要涉及 @mut 视图或 @ref 的绑定，默认拷贝；只有双侧都是 @ref 才是别名**。

## 引用 @ref（别名共享）

想共享同一对象（别名语义）用 `@ref`：

```subl
@ref var a: @mut Counter = Counter(0)
@ref var b: @mut Counter = a             // 双侧 @ref：同一对象
b.n = 1
print(toStr(a.n))                        // 1（别名共享）

@ref var c: Counter = a                  // 可变引用 → 不可变引用：仍是别名
a.n = 7
print(toStr(c.n))                        // 7（能观察到修改，但没有修改权）
```

引用变量与非引用变量互传（任一侧不是 `@ref`）会触发深拷贝：

```subl
@ref var a: @mut Counter = Counter(0)
var b: @mut Counter = a                  // @ref → 非 @ref：深拷贝
a.n = 1
print(toStr(b.n))                        // 0
```

## 上下文对可变性的态度

上下文系统是给函数式风格用的——**可变变量与 @ref 变量的绑定等式不可靠，一律不注入** Γ：

- `@mut var m = 5` 不会注入 `m = 5`（值会变，等式保不住），但保留自反；
- 结构体的可变视图绑定（含 `@mut struct` 的实例）同样不注入字段等式；
- `@atomic` 不破坏不可变性（原子性注解见《异步》）。

想让可变值配合证明，最干净的做法是**把可变性割掉**：写一个新的不可变变量承接它（`var snapshot = m`），让编译器看见你亲自冻结了值。

## 逃逸 unchecked

不方便标 `@mut`/`@impure` 时可以用 `unchecked` 显式逃逸——这一刻起你对正确性负责：

```subl
var frozen = 0
unchecked frozen = frozen + 1    // 绕过 E-IMMUT-ASSIGN（逃逸单条语句的检查）
```

> `unchecked` 是手术刀不是瑞士军刀：逃逸过的代码不再受编译器保护，确认不了行为时宁可改标注。

## 建议

- 优先写**纯 + 不可变**代码（函数式风格），少用 `@mut`/`@ref`。
- 把可变性"割掉"：需要新值时，写一个新不可变变量。
- 证明命题时尽量用不可变值——可变变量/`@ref` 的等式命题在上下文里不成立。
- 真需要就地修改（计数器、缓存、共享状态），`@mut struct` + `@mut` 视图 + 必要时 `@ref` 别名，把可变性关进最小的笼子。
