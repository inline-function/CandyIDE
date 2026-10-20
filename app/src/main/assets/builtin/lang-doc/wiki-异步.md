# 异步

凝华的异步目标是"够用"：编译目标只有 JS，做不了太复杂——不过本来也不需要做得很复杂。

> 本文对应根文档《异步》。所有示例均以 v4 编译器（commit 802b43a，674 项测试全绿）逐条验证：`compile <工程目录>` 后用 node 运行。

## 异步函数 @async

标注 `@async` 的函数是**异步函数**。**没有 `await` 关键词**——调用异步函数自动 await：

```subl
@async
fun worker(name: Str): Null {
    print("worker " + name)
}

@impure
fun main() {
    worker("a")              // 异步调用，自动 await（不需要写 await）
}
```

- 只有异步函数可以调用异步函数（同步函数里调报 `E-NEED-ASYNC`）；
- `main()` 天然是 async 上下文；`Task` 体也天然 async；
- 需要副作用的异步函数同时标 `@impure @async`。

## 任务 Task

```subl
var t = Task {                // 创建任务（此时不启动）
    print("child task")
}
t.start()                     // 启动（不阻塞）
t.join()                      // 挂起直到完成，取返回值
print(toStr(t.isAlive()))     // 是否仍在运行
```

- `Task { … }` 是块表达式（不是 lambda），体天然 async；
- `join()` 前必须先 `start()`，否则 `E-NOT-STARTED`；在同步上下文调 `join()` 报 `E-NEED-ASYNC`；
- Task 体可以有返回值：`var t = Task { 42 }`，`t.join()` 拿到 42。

## 通道 Channel

协调异步数据传递：

```subl
var ch: Channel[Nat] = Channel<Nat>(2)     // 有缓冲通道，容量 2（Channel<T>(cap)）
ch.send(1)                                 // 缓冲满时发送方挂起
ch.send(2)
var v: Nat = ch.receive()                  // 缓冲空时接收方挂起
ch.close()                                 // 关闭
print(toStr(ch.isClosed()))                // true
```

- 类型标注写 `Channel[Nat]`（类型位用 `[]`），创建调用写 `Channel<Nat>(容量)`（调用位类型实参用 `<>`）；容量 0 是无缓冲通道（发送/接收必须配对，否则挂起等待）；
- FIFO 保序：先发送先接收；
- **本地未关闭通道的 `receive()` 智能转换为 `T`**（决策 93，直接拿到解包值）；通道来自参数或已关闭时返回 `Optional[T]`，要用 when 解包：

```subl
@impure @async
fun consume(ch: Channel[Nat]): Null {
    var x = ch.receive()          // 参数位 → Optional[Nat]（保守）
    when (x) {
        is Some(v) => print("val=" + toStr(v))
        None => print("closed")
    }
}
```

- `close()` 之后的 send/receive 报 `E-CHANNEL-CLOSED`。

`core.async` 还提供配套工具函数（`@impure @async`）：`channelFromList`、`channelDrain`、`channelPipe`（管道转投）、`sendAll`（批量发送）。

## 锁 Mutex

防止异步情况下的数据竞争：

```subl
@mut struct Counter(@mut n: Nat)

@impure @async
fun bump(m: Mutex, c: @mut Counter): Null {
    m.withLock(fun() {
        c.n = c.n + 1                    // 临界区，退出即解锁
    })
}

@impure
fun main() {
    var m = Mutex()
    var c: @mut Counter = Counter(0)
    var t = Task { bump(m, c) }
    t.start()
    t.join()
}
```

- `Mutex()` 创建（无类型参数）；`lock()`/`unlock()` 是底层加解锁，一般直接用 `withLock(f)`；
- `withLock` 的参数类型是 `@impure () => Null`——传 lambda（`{ … }`）或匿名函数（`fun() { … }`）皆可，纯度随函数值传递；
- `lock`/`unlock`/`withLock` 本身是挂起操作，需要异步上下文。

## 原子操作 @atomic

原子操作是不可分割的最小操作。为类型标注 `@atomic` 表示对它的读写是原子的：

```subl
@mut var counter: @atomic Nat = 0       // @atomic 是透明类型标记
counter = counter + 1
```

- `@atomic` 修饰**类型**（透明标记，运行时无额外包装），不改变类型本身；自然数、整数、有理数、字符串、容器类型都可以标注；
- `@atomic` 只是原子性标记——变量仍需 `@mut` 才能赋值（`@atomic` 不含可变性）；
- JS 单线程按值读写，原子性不破坏不可变性。

## 流 Flow

惰性冷流——`collect` 时才执行生成器体：

```subl
var fl = Flow {
    emit(1)                              // emit 只能在 Flow 体内（翻译为 yield）
    emit(2)
    emit(3)
}

fl.collect(fun(x: Nat) {                 // 消费每个发射值
    print("flow " + toStr(x))
})

var doubled = fl.map({ x => x * 2 })     // 派生冷流（不立即执行）
doubled.collect(fun(x: Nat) {
    print("doubled " + toStr(x))
})
```

- 创建语法 `Flow { emit(v); … }`（块表达式，体为 async 生成器）；
- `map(f)` 派生新冷流；`collect(f)` 消费；`emit(v)` 仅在 Flow 体内合法；
- `core.async` 的消费工具：`collectToList`（收集为列表）、`collectCount`（计数）、`collectAll`（谓词全称判定）。

> 回调函数两种写法都合法：lambda `{ x => … }` 与匿名函数 `fun(x: Nat) { … }`。传入 `@impure` 函数类型参数的回调可以含 `print` 等副作用（纯度随期望类型传递）。

## 任务组合与延时

`core.async` 的内建工具函数（`@root`，编译器内建 JS 实现）：

```subl
delay(10)                                // 显式延时（毫秒）
runBoth(f, g)                            // 先启动 f（不阻塞），主路径继续 g，join 等 f 收尾
runSeq(f, g)                             // 先做完 f 再做 g（串行语义）
```

## 一句话

- 异步函数用 `@async`，调用自动 await，不用写 `await`。
- 并发用 `Task`（`start` 后 `join`）；协调用 `Channel`；防竞争用 `Mutex`/`@atomic`；惰性流用 `Flow`。
