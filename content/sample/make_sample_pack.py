#!/usr/bin/env python3
"""Writes a SMALL PLACEHOLDER pack (content/packs/relanti-b.json) for testing the apps.
Superseded by the real pipeline output; only run this to get the sample back.

These questions were written by hand to exercise the app. They are NOT checked
against the BOE and must be replaced by the real pipeline's output
(see the "Source official DGT question material" work). Every item carries
source.note = "MUESTRA" and the pack has placeholder: true, which the apps show.
"""
import json, os

RGC = ("RGC", "BOE-A-2003-23514")
NOTE = "MUESTRA: pregunta de prueba, artículo por verificar"

def src(art):
    return {"law": RGC[0], "boe": RGC[1], "block": "a%s" % art, "ref": "RGC, art. %s" % art,
            "checkedOn": None, "note": NOTE}

ETAPAS = [
    ("SE", "Villaseñal", "Señales", "ciudad", True),
    ("PR", "Cruce de la Prioridad", "Prioridad", "ciudad", True),
    ("VE", "Recta de la Velocidad", "Velocidad", "meseta", True),
    ("AD", "Paso del Adelanto", "Adelantamientos", "costa", False),
    ("PA", "Plaza del Aparcamiento", "Parada y estacionamiento", "ciudad", False),
    ("AL", "Faro Nocturno", "Alumbrado", "montana", False),
    ("VH", "Taller", "Vehículos", "meseta", False),
    ("FH", "Mirador Alerta", "Factores de riesgo", "montana", False),
    ("PS", "Área PAS", "Seguridad pasiva", "costa", False),
    ("PP", "Oficina de Puntos", "Permisos y puntos", "ciudad", False),
    ("EC", "Valle Eco", "Conducción eficiente", "costa", False),
    ("EX", "Centro de examen", "Simulacros", "meseta", False),
]

TRAMOS = [
    ("SE-101", "SE", 1, "Señales de peligro", "T-SE-101"),
    ("SE-102", "SE", 2, "Prohibición y obligación", "T-SE-102"),
    ("PR-101", "PR", 1, "Intersecciones y glorietas", "T-PR-101"),
    ("VE-101", "VE", 1, "Límites de velocidad", "T-VE-101"),
    ("AD-101", "AD", 1, "Adelantar", "T-AD-101"),
]

TRUCOS = [
    ("T-SE-101", "reading", "«Siempre» y «nunca» son señales de peligro.",
     "Las normas tienen excepciones: un agente manda más que un semáforo, y una señal más que la norma general.", 133),
    ("T-SE-102", "memory", "Rojo prohíbe, azul obliga.",
     "Círculo con borde rojo: prohibición. Círculo azul: obligación. El color ya te da media respuesta.", 154),
    ("T-PR-101", "memory", "Sin señales, manda la derecha.",
     "En un cruce sin señalizar cede a quien viene por tu derecha. En la glorieta manda quien ya está dentro.", 57),
    ("T-VE-101", "reading", "La velocidad máxima es un techo, no un objetivo.",
     "Lluvia, niebla o una salida de colegio: casi siempre la respuesta es adaptar la velocidad.", 45),
    ("T-AD-101", "memory", "Ciclista: 1,5 metros.",
     "Para adelantar a un ciclista deja al menos 1,5 m: piensa en un ciclista con los brazos abiertos.", 85),
]

# (question, [A, B, C], answer index, explanation, article)
ITEMS = {
    "SE-101": [
        ("¿Qué forma tienen, en general, las señales de advertencia de peligro?",
         ["Circular con borde rojo", "Triangular con borde rojo y un vértice hacia arriba", "Cuadrada con fondo azul"],
         1, "El triángulo con borde rojo avisa de un peligro más adelante.", 149),
        ("Fuera de poblado, ¿a qué distancia del peligro se suelen colocar estas señales?",
         ["Justo en el punto del peligro", "Entre 150 y 250 metros antes", "A 500 metros o más"],
         1, "Te dan tiempo para reducir la velocidad antes de llegar.", 149),
        ("Una señal de peligro con fondo amarillo indica que…",
         ["es provisional, normalmente por obras", "solo vale de noche", "es una recomendación"],
         0, "El fondo amarillo es señalización circunstancial, como en obras.", 133),
        ("Al ver una señal de advertencia de peligro, debe…",
         ["mantener la velocidad", "extremar la precaución y adaptar la velocidad", "detenerse siempre"],
         1, "Avisan de un peligro: adapta la marcha; no obligan a parar.", 149),
        ("La señal de peligro con la silueta de dos niños avisa de…",
         ["un parque de atracciones", "un lugar frecuentado por niños, como un colegio", "un paso de peatones elevado"],
         1, "Puede haber niños cruzando sin mirar.", 149),
        ("Si un agente le indica que avance y el semáforo está en rojo, ¿qué hace?",
         ["Espero al verde", "Avanzo: las indicaciones del agente prevalecen", "Toco el claxon y espero"],
         1, "Primero el agente, luego la señalización circunstancial, semáforos, señales y marcas.", 133),
        ("Si una señal provisional de obras contradice a una señal fija, prevalece…",
         ["la señal fija", "la provisional de obras", "la que esté más cerca"],
         1, "La señalización circunstancial va por delante de la fija.", 133),
        ("La señal de «estrechamiento de calzada» avisa de que…",
         ["la vía va a tener menos anchura", "empieza una autovía", "está prohibido adelantar"],
         0, "Prepárate para ajustar tu posición y velocidad.", 149),
    ],
    "SE-102": [
        ("Las señales de prohibición suelen ser…",
         ["triangulares con borde rojo", "circulares con borde rojo", "rectangulares con fondo verde"],
         1, "Círculo con borde rojo: algo está prohibido.", 154),
        ("Las señales de obligación suelen ser…",
         ["circulares con fondo azul", "octogonales rojas", "cuadradas con fondo blanco"],
         0, "El círculo azul indica algo que debes hacer.", 155),
        ("Una señal circular blanca con una franja diagonal negra indica…",
         ["el fin de una prohibición", "una prohibición de giro", "una zona peatonal"],
         0, "Las señales de fin de prohibición llevan la franja diagonal.", 154),
        ("Ante la señal de STOP debe…",
         ["reducir y pasar si no viene nadie", "detenerse siempre y ceder el paso", "detenerse solo si hay tráfico"],
         1, "El STOP obliga a parar del todo, venga o no alguien.", 151),
        ("¿Qué forma tiene la señal de «ceda el paso»?",
         ["Octogonal", "Triangular con un vértice hacia abajo", "Circular con borde rojo"],
         1, "Es la única señal triangular invertida.", 151),
        ("Una señal circular con borde rojo y el número 50 indica…",
         ["velocidad mínima de 50 km/h", "velocidad máxima de 50 km/h", "velocidad recomendada de 50 km/h"],
         1, "Borde rojo prohíbe: no puedes pasar de 50.", 154),
        ("Una señal circular azul con el número 30 indica…",
         ["velocidad máxima de 30 km/h", "velocidad mínima obligatoria de 30 km/h", "zona 30 recomendada"],
         1, "El azul obliga: no circules por debajo de 30 si nada lo impide.", 155),
        ("Una señal de prohibición empieza a aplicarse…",
         ["desde el lugar donde está colocada", "100 metros después", "en la siguiente intersección"],
         0, "Rige desde su emplazamiento.", 154),
    ],
    "PR-101": [
        ("En una intersección sin señalizar, ¿quién tiene preferencia?",
         ["El que viene por la izquierda", "El que viene por la derecha", "El vehículo más grande"],
         1, "Sin señales, cede a quien se aproxima por tu derecha.", 57),
        ("Al llegar a una glorieta, ¿quién tiene preferencia?",
         ["Quien ya circula por la glorieta", "Quien entra en ella", "Quien viene por la derecha"],
         0, "Dentro de la glorieta tienen preferencia sobre los que entran.", 57),
        ("Una vía pavimentada se cruza con un camino de tierra. Preferencia:",
         ["el camino de tierra", "la vía pavimentada", "quien llegue primero"],
         1, "Los que circulan por vía pavimentada tienen preferencia sobre los que salen de una sin pavimentar.", 57),
        ("Para entrar en una autovía desde un carril de aceleración debe…",
         ["tener preferencia por estar acelerando", "ceder el paso a quienes circulan por la autovía", "detenerse siempre al final del carril"],
         1, "Te incorporas tú: cede el paso a quienes ya circulan.", 72),
        ("Al girar a la derecha, un ciclista sigue recto por el arcén. ¿Quién pasa primero?",
         ["Usted, porque gira", "El ciclista", "Quien vaya más rápido"],
         1, "Al girar debes ceder el paso a los ciclistas que siguen recto.", 64),
        ("Una ambulancia con luces y sirena se acerca. Usted debe…",
         ["seguir igual", "facilitarle el paso apartándose", "acelerar para no estorbar"],
         1, "Los vehículos en servicio de urgencia tienen prioridad.", 68),
        ("Con el semáforo en ámbar fijo, debe…",
         ["acelerar para pasar", "detenerse si puede hacerlo con seguridad", "pasar siempre"],
         1, "Ámbar significa parar, salvo que no puedas hacerlo con seguridad.", 146),
        ("En una intersección sin señalizar, los vehículos que circulan sobre raíles…",
         ["tienen preferencia", "deben ceder siempre", "solo pasan con semáforo"],
         0, "Tranvías y similares tienen preferencia en esos casos.", 57),
    ],
    "VE-101": [
        ("¿Velocidad máxima de un turismo en autopista o autovía?",
         ["100 km/h", "120 km/h", "130 km/h"],
         1, "120 km/h para turismos y motos.", 48),
        ("¿Velocidad máxima de un turismo en una carretera convencional?",
         ["80 km/h", "90 km/h", "100 km/h"],
         1, "En convencionales el máximo para turismos es 90 km/h.", 48),
        ("En una calle con un solo carril por sentido, el límite general es…",
         ["20 km/h", "30 km/h", "50 km/h"],
         1, "Un carril por sentido en ciudad: 30.", 50),
        ("En una calle de plataforma única (calzada y acera al mismo nivel), el límite es…",
         ["20 km/h", "30 km/h", "40 km/h"],
         0, "Plataforma única: 20.", 50),
        ("En una calle con dos o más carriles por sentido, el límite general es…",
         ["30 km/h", "50 km/h", "60 km/h"],
         1, "Dos o más carriles por sentido: 50.", 50),
        ("Para adelantar en una carretera convencional, ¿puede superar el límite?",
         ["Sí, en 20 km/h", "No, nunca", "Sí, si el adelantamiento es corto"],
         1, "Ya no existe el margen de 20 km/h para adelantar.", 51),
        ("En autopista, un turismo no debe circular, sin motivo, a menos de…",
         ["40 km/h", "60 km/h", "80 km/h"],
         1, "No vayas a menos de la mitad del límite genérico de la vía.", 49),
        ("La distancia de seguridad con el vehículo de delante debe permitirle…",
         ["adelantarle en cualquier momento", "detenerse sin chocar si frena de golpe", "ver su matrícula"],
         1, "Deja espacio para frenar sin colisionar.", 54),
    ],
    "AD-101": [
        ("Al adelantar a un ciclista fuera de poblado, la separación lateral mínima es…",
         ["1 metro", "1,5 metros", "2 metros"],
         1, "1,5 m: piensa en un ciclista con los brazos abiertos.", 85),
        ("Por regla general, se adelanta…",
         ["por la derecha", "por la izquierda", "por donde haya más espacio"],
         1, "Se adelanta por la izquierda, salvo excepciones.", 82),
        ("Puede adelantar por la derecha a un vehículo que…",
         ["va despacio", "ha indicado que gira a la izquierda y se ha desplazado", "es un camión"],
         1, "Es una de las excepciones a adelantar por la izquierda.", 82),
        ("En una curva de visibilidad reducida, adelantar está…",
         ["permitido si va rápido", "prohibido si hay que invadir el sentido contrario", "permitido de día"],
         1, "Sin visibilidad suficiente no se adelanta invadiendo el otro sentido.", 87),
        ("En un paso para peatones señalizado, adelantar está…",
         ["permitido si no hay peatones", "prohibido", "permitido a motos"],
         1, "No se adelanta en los pasos de peatones señalizados.", 87),
        ("Si le están adelantando, usted debe…",
         ["acelerar un poco", "no aumentar la velocidad", "frenar en seco"],
         1, "El adelantado no debe aumentar la velocidad.", 86),
        ("Después de adelantar, vuelva a su carril…",
         ["cuanto antes, aunque esté cerca", "de forma gradual, sin obligar al adelantado a frenar", "solo tras 1 km"],
         1, "Vuelve de forma progresiva y señalizándolo.", 85),
        ("Antes de adelantar, lo primero es…",
         ["tocar el claxon", "comprobar que hay espacio y visibilidad suficientes", "pegarse al de delante"],
         1, "Asegúrate de poder hacerlo sin peligro antes de empezar.", 84),
    ],
}

pack = {
    "format": "relanti-pack",
    "schemaVersion": 1,
    "contentVersion": 1,
    "licence": "B",
    "placeholder": True,
    "attribution": {
        "boe": "Basado en datos de la Agencia Estatal Boletín Oficial del Estado",
        "boeUrl": "https://www.boe.es",
        "consolidatedNotice": "Paquete de MUESTRA para pruebas: preguntas sin verificar.",
    },
    "etapas": [{"code": c, "order": i + 1, "name": n, "topic": t, "landscape": l, "free": f}
               for i, (c, n, t, l, f) in enumerate(ETAPAS)],
    "tramos": [{"code": c, "etapa": e, "order": o, "title": t, "truco": tr} for c, e, o, t, tr in TRAMOS],
    "trucos": [{"id": i, "kind": k, "title": t, "body": b, "source": src(a)} for i, k, t, b, a in TRUCOS],
    "items": [],
}
for tramo, rows in ITEMS.items():
    for n, (q, opts, ans, expl, art) in enumerate(rows, 1):
        pack["items"].append({
            "id": "%s-%02d" % (tramo, n), "tramo": tramo, "question": q, "options": opts, "answer": ans,
            "explanation": expl, "image": None, "novedad": False, "source": src(art),
        })

out = os.path.join(os.path.dirname(__file__), "..", "packs", "relanti-b.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(pack, f, ensure_ascii=False, indent=1)
    f.write("\n")
print("wrote", os.path.normpath(out), len(pack["items"]), "items")
