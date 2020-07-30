from datetime import datetime

from opendc.models.prefab import Prefab
from opendc.models.user import User
from opendc.util.database import Database
from opendc.util.rest import Response


def GET(request):
    """Return all prefabs the user is authorized to access"""

    user = User.from_id(request.google_id)

    user.check_exists()

    own_prefabs = Prefab.get_all({'authorId' : user.get_id()})
    public_prefabs = Prefab.get_all({'visibility' : 'public'})

    authorizations = { "authorizations" : []}

    authorizations["authorizations"].append(own_prefabs)
    authorizations["authorizations"].append(public_prefabs)

    return Response(200, 'Successfully fetched authorizations.', authorizations)
