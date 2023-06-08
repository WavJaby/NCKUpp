# NCKUpp

A new site for NCKU course enrollment system

[To NCKU++](https://wavjaby.github.io/NCKUpp)

## API usage

### Api Response Object

```
{
    "success": boolean
    "data": {}|null
    "msg": string
    "err": [string]
    "warn": [string]
}
```

Api URL: https://api.simon.chummydns.com/api

<details>
<summary><code>GET</code> <code><b>/search</b></code> <code>Search course data</code></summary>

##### Parameters

> | name         | type     | data type | description                                               |
> |--------------|----------|-----------|-----------------------------------------------------------|
> | `courseName` | optional | string    | Course name                                               |
> | `instructor` | optional | string    | Instructor name                                           |
> | `dayOfWeek`  | optional | int       | Day of week 1~7                                           |
> | `dept`       | optional | string    | Department ID                                             |
> | `grade`      | optional | string    | Course for grade                                          |
> | `section`    | optional | int[]     | Section of day [1~16]                                     |
> | `serial`     | optional | UrlEncode | Serial IDs {DEPT_ID}={SERIAL},{SERIAL}&{DEPT_ID}={SERIAL} |

At least one parameter needs to be provided
</details>


<details>
<summary><code>GET</code> <code><b>/alldept</b></code> <code>Get all department</code></summary>
</details>


<details>
<summary><code>GET</code> <code><b>/nckuhub</b></code> <code>Get nckuhub data</code></summary>

##### Parameters

> | name | type     | data type | description      |
> |------|----------|-----------|------------------|
> | `id` | optional | string    | Course serial ID |

No parameter provide, will return NCKUHUB_ID corresponding to the CourseSerialID
</details>


<details>
<summary><code>GET</code> <code><b>/login</b></code> <code>Get login state</code></summary>

##### Parameters

> | name | type     | data type | description                   |
> |------|----------|-----------|-------------------------------|
> | `m`  | optional | string    | Login mode, legal value: c, i |

##### Login mode

* c: Course
* i: StudentIdentification

</details>


<details>
<summary><code>POST</code> <code><b>/login</b></code> <code>Login</code></summary>

##### Parameters

> | name | type     | data type | description                   |
> |------|----------|-----------|-------------------------------|
> | `m`  | optional | string    | Login mode, legal value: c, i |

##### Login mode

* c: Course
* i: StudentIdentification

##### Content

```
username=[Account]&password=[Password]
```

</details>


<details>
<summary><code>GET</code> <code><b>/v0/socket</b></code> <code>Notify system api</code></summary>
</details>