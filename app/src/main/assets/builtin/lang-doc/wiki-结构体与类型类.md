# 结构体与类型类

> 所有示例均以 v4 编译器（commit 802b43a，674 项测试全绿）逐条验证。

## 结构体 struct

值类型，可带字段默认值与伴生命题：

```subl
struct User(name: Str, age: Nat)                    // 基础结构体
struct PosPoint(x: Nat, y: Nat)<x > 0, y > 0>       // 伴生命题：构造时证明
struct Item(count: Nat = 1)                         // 字段默认值
```

构造与读取（两种构造形态：位置式与命名字段）：

```subl
var u1 = User("Alice", 25)              // 位置式构造
var u2 = User(name = "Bob")             // 命名字段构造，age 取默认值 0
print(u1.name + " " + toStr(u1.age))    // Alice 25
```

- 字段默认值只属于 struct；函数/类型类成员的形参**不允许**默认值（`E-DEFAULT-PARAM`）。
- 构造成功后，字段等式与伴生命题注入上下文（见《上下文系统》）。

## 枚举 enum

```subl
enum Color { Red(); Green(); Blue() }

// 带数据的枚举（ADT）——分带括号与不带括号两种构造子写法
enum Result[T, E] { Ok(T); Err(E) }
```

- 跨模块自动解析：`Ok` /`Err` 等裸名构造子在可见模块（含 `core` 标准库）里依次查找；多枚举同名构造子有歧义时用 `枚举::构造子` 明确指定。
- 枚举是 when 解构与穷尽性检查的主角，详见《模式匹配》。

## 类型类 class 与 impl

`class` 声明一个**类型类**（接口/协议），`impl` 为具体类型实现它：

```subl
class Show {
    fun show(): Str
}

impl Show for User {
    fun show(): Str = "{ name=" + self.name + ", age=" + toStr(self.age) + " }"
}

var u = User("Alice", 25)
print(u.show())                 // { name=Alice, age=25 }
```

- impl 方法带**隐式 self**：方法体内可用 `self` 指代接收者，也可以**裸用同名字段**（`name` ≡ `self.name`）。
- impl 必须实现类型类声明的全部方法，签名一致；多了报 `E-IMPL-EXTRA`，少了或不一致报 `E-IMPL-INCOMPLETE` / `E-IMPL-MISMATCH`。
- **双形态调用**：`u.show()`（方法糖）与 `show(u)`（自由式）等价。

方法的外部参数照常声明——接收者始终由调用点自动填入：

```subl
class Eq[T] {
    fun eq(other: T): Bool
}

impl Eq for User {
    fun eq(other: User): Bool = self.name == other.name
}

var a = User("Alice", 25)
var b = User("Bob", 0)
print(toStr(a.eq(b)))           // false
print(toStr(eq(a, a)))          // true（自由式）
```

## 扩展方法

`impl` 一个**类型本身**（不实现类型类）就是扩展方法：

```subl
impl User {
    fun isAdult(): Bool = self.age >= 18
}

u.isAdult()                     // true
```

## 多态与类型参数

类型参数写在 `[]` 里——声明位与类型代入位同族：

```subl
fun firstOrAny[A](xs: List[A], d: A): A = …
```

高阶类型（HKT）用 kind 应用槽 `_` 声明与实现：

```subl
class Functor[F[_]] {
    fun map[A, B](fa: F[A], f: (A) => B): F[B]
}

impl Functor for List[a] {
    fun map[A, B](fa: List[A], f: (A) => B): List[B] = when(fa) {
        Nil => Nil()
        is Cons(h, t) => Cons(f(h), t.map(f))
    }
}
```

- HKT 形态下，impl 的 self 位（`List[a]` 的 `a`）是隐式绑定的类型变量，直接写小写名即可。
- 类型类还支持**默认实现**（成员带函数体，impl 缺方法时补齐）与子类型类（`class Monad[F] : Functor[F]`，须先实现父类）。

## 容器上的类型类

`core` 标准库的 `Functor` / `Foldable` / `Monad` 类型类统一了 List/Set/Optional 的接口，同名自由函数按接收者重载分派：

```subl
var xs = listOf(1, 2, 3, 4)
print("size = " + toStr(size(xs)))                       // 4
print("fold = " + toStr(fold(xs, 0, { acc, x => acc + x })))   // 10
print("method sugar = " + toStr(xs.fold(0, { acc, x => acc + x })))   // 10
var doubled = xs.map({ x => x * 2 })                     // List[Nat]
var o = Some(7)
print("opt fold = " + toStr(o.fold(0, { acc, x => acc + x })))       // 7
```

回调函数的函数类型写作 `(A) => B`；lambda 文档统一用 `=>`（`->` 也兼容）。

## 类型别名（决策 30）

给已有类型起个短名：`type 别名 = 目标类型`。别名是纯类型层设施——编译期展开，**不生成任何运行时代码**。

### 简单别名

```subl
type Id = Nat
type Celsius = Rat
type Fahrenheit = Rat

fun c2f(c: Celsius): Fahrenheit = c * 9.0 / 5.0 + 32.0

var id: Id = 41
var temp: Celsius = 37.0
```

别名只是更可读的记号，类型检查时按目标类型对待——`Celsius` 与 `Rat` 互通。

### 展开容器类型

```subl
type NatOpt = Optional[Nat]

var o: NatOpt = Some(7)          // 展开：Optional[Nat]

fun unwrap(o: NatOpt): Nat = when(o) {
    is Some(v) => v
    None => 0
}
```

### 泛型头别名

别名可以自带**泛型头**，使用点按位置代入展开：

```subl
type Box[a] = Optional[a]        // 泛型头：a 是类型变量
type Dict[k, v] = Map[k, v]      // 多参数泛型头

var b: Box[Nat] = Some(9)        // Box[Nat] 展开为 Optional[Nat]
var d: Dict[Str, Nat] = put(put(mapEmpty<Str, Nat>(), "a", 1), "b", 2)

fun lookup(d: Dict[Str, Nat], key: Str): Nat = when(get(d, key)) {
    is Some(v) => v
    None => 0
}
```

- 右值类型可以前向引用别名；别名与已有 struct/enum/class/内置类型重名报 `E-DUP-DECL`。
- 使用点的**类型代入用 `[]`**（`Box[Nat]`、`Dict[Str, Nat]`），与声明位 `type Box[a]` 同族；**函数调用的类型实参用 `<>`**（`mapEmpty<Str, Nat>()`、`Channel<Nat>(2)`）——两套括号职责分明，`<>` 还兼任命题施加与显式供给。

### 当前限制

泛型头别名是本次新增能力，还有一个已知 rough edge：

- **函数返回类型若直接写别名，调用点得到的类型保留别名名（未展开）**，再给它加类型标注或传给另一个函数会报 `E-TYPE-MISMATCH`：

```subl
type NatOpt = Optional[Nat]
fun mk(): NatOpt = Some(7)

var o = mk()                    // ✅ 不加标注可以
// var o: NatOpt = mk()         // ❌ E-TYPE-MISMATCH（标注已展开、值侧未展开）
```

规避方式：返回类型写展开后的真实类型（`fun mk(): Optional[Nat]`），或者调用时不加标注。参数位、字段位、变量标注位不受影响。

## 方法糖解析顺序

`x.f(…)` 的解析顺序：

1. 同名自由函数/扩展方法，且首形参类型匹配接收者（优先）
2. 类型类字典里的方法（按 self 类型解析实例）
3. 结构体自身的字段（字段访问）

```subl
// 同名时：自由函数 bump(c) 优先于类型类方法 bump()
@impure fun bump(c: @mut Counter): Null { c.n = c.n + 1 }
@mut class Bumpable { @mut fun bump(): Null }
impl Bumpable for Counter { @mut fun bump(): Null { self.n = self.n + 10 } }
// a.bump() 命中自由函数 bump(a)（+1），不是类型类方法（+10）
```

调用形态上，`x.f(a)` 与 `f(x, a)` 永远等价（扩展调用法）；没有接收者时按自由函数解析。
