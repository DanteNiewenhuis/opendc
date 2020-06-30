from opendc.models.model import Model
from opendc.models.user import User
from opendc.util.database import DB
from opendc.util.exceptions import ClientError
from opendc.util.rest import Response


class Simulation(Model):
    """Model representing a Simulation."""

    collection_name = 'simulations'

    def check_user_access(self, google_id, edit_access):
        """Raises an error if the user with given [google_id] has insufficient access.

        :param google_id: The Google ID of the user.
        :param edit_access: True when edit access should be checked, otherwise view access.
        """
        user = User.from_google_id(google_id)
        authorizations = list(
            filter(lambda x: str(x['simulationId']) == str(self.get_id()), user.obj['authorizations']))
        if len(authorizations) == 0 or (edit_access and authorizations[0]['authorizationLevel'] == 'VIEW'):
            raise ClientError(Response(403, "Forbidden from retrieving simulation."))

    def get_all_authorizations(self):
        """Get all user IDs having access to this simulation."""
        return [
            user['_id'] for user in DB.fetch_all({'authorizations': {
                'simulationId': self.get_id()
            }}, User.collection_name)
        ]
